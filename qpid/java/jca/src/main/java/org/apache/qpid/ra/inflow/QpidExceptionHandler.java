/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.ra.inflow;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.jms.XAConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.resource.ResourceException;
import javax.resource.spi.endpoint.MessageEndpointFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.ra.QpidResourceAdapter;
import org.apache.qpid.ra.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class QpidExceptionHandler implements ExceptionListener
{
    private static final Logger _log = LoggerFactory.getLogger(QpidExceptionHandler.class);

    public static final Method ONMESSAGE;

    protected final MessageEndpointFactory _endpointFactory;

    protected Connection _connection;

    protected ConnectionFactory _factory;

    protected Destination _destination;
    
    protected final QpidResourceAdapter _ra;

    protected final QpidActivationSpec _spec;

    protected boolean _isDeliveryTransacted;

    protected final AtomicBoolean _deliveryActive = new AtomicBoolean(false);

    protected boolean _isTopic = false;
    
    // Whether we are in the failure recovery loop
    protected AtomicBoolean _inFailure = new AtomicBoolean(false);

    //Whether or not we have completed activating
    protected AtomicBoolean _activated = new AtomicBoolean(false);

    static
    {
       try
       {
          ONMESSAGE = MessageListener.class.getMethod("onMessage", new Class[] { Message.class });
       }
       catch (Exception e)
       {
          throw new RuntimeException(e);
       }
    }
   
    public abstract void setup() throws Exception;
    public abstract void start() throws Exception;
    public abstract void stop();
    
    protected QpidExceptionHandler(QpidResourceAdapter ra,
                                   QpidActivationSpec spec,
                                   MessageEndpointFactory endpointFactory) throws ResourceException
    {
        this._ra = ra;
        this._spec = spec;
        this._endpointFactory = endpointFactory;
        
        try
        {
           _isDeliveryTransacted = endpointFactory.isDeliveryTransacted(QpidActivation.ONMESSAGE);
        }
        catch (Exception e)
        {
           throw new ResourceException(e);
        }
   
        
    }
    
    public void onException(JMSException e)
    {
        if(_activated.get())
        {
            handleFailure(e) ;
        }
        else
        {
            _log.warn("Received JMSException: " + e + " while endpoint was not activated.");
        }
    }
    
    /**
     * Handles any failure by trying to reconnect
     *
     * @param failure the reason for the failure
     */
    public void handleFailure(Throwable failure)
    {
       if(doesNotExist(failure))
       {
          _log.info("awaiting topic/queue creation " + _spec.getDestination());
       }
       else
       {
          _log.warn("Failure in Qpid activation " + _spec, failure);
       }
       int reconnectCount = 0;
       int setupAttempts = _spec.getSetupAttempts();
       long setupInterval = _spec.getSetupInterval();

       // Only enter the failure loop once
       if (_inFailure.getAndSet(true))
          return;
       try
       {
          while (_deliveryActive.get() && (setupAttempts == -1 || reconnectCount < setupAttempts))
          {
             teardown();

             try
             {
                Thread.sleep(setupInterval);
             }
             catch (InterruptedException e)
             {
                _log.debug("Interrupted trying to reconnect " + _spec, e);
                break;
             }

             _log.info("Attempting to reconnect " + _spec);
             try
             {
                setup();
                _log.info("Reconnected with Qpid");
                break;
             }
             catch (Throwable t)
             {
                if(doesNotExist(failure))
                {
                   _log.info("awaiting topic/queue creation " + _spec.getDestination());
                }
                else
                {
                   _log.error("Unable to reconnect " + _spec, t);
                }
             }
             ++reconnectCount;
          }
       }
       finally
       {
          // Leaving failure recovery loop
          _inFailure.set(false);
       }
    }

    /**
     * Check to see if the failure represents a missing endpoint
     * @param failure The failure.
     * @return true if it represents a missing endpoint, false otherwise
     */
    protected boolean doesNotExist(final Throwable failure)
    {
       return (failure instanceof AMQException) && (((AMQException)failure).getErrorCode() == AMQConstant.NOT_FOUND) ;
    }

    protected boolean isXA()
    {
        return _isDeliveryTransacted && !_spec.isUseLocalTx();
    }
    
    protected void setupConnection() throws Exception
    {
        this._connection = (isXA()) ? ((XAConnectionFactory)_factory).createXAConnection() : _factory.createConnection();        
    }
        
    protected synchronized void teardown()
    {
       _log.debug("Tearing down " + _spec);

       try
       {
          if (_connection != null)
          {
             _connection.stop();
          }
       }
       catch (Throwable t)
       {
          _log.debug("Error stopping connection " + Util.asString(_connection), t);
       }

       try
       {
          if (_connection != null)
          {
             _connection.close();
          }
       }
       catch (Throwable t)
       {
          _log.debug("Error closing connection " + Util.asString(_connection), t);
       }
       if (_spec.isHasBeenUpdated())
       {
          _factory = null;
       }
       _log.debug("Tearing down complete " + this);
    }

    protected void setupCF() throws Exception
    {
       if (_spec.isHasBeenUpdated())
       {
          _factory = _ra.createAMQConnectionFactory(_spec);
       }
       else
       {
          _factory = _ra.getDefaultAMQConnectionFactory();
       }
    }
    
    protected void setupDestination() throws Exception
    {

       String destinationName = _spec.getDestination();
       String destinationTypeString = _spec.getDestinationType();

       if (_spec.isUseJNDI())
       {
          Context ctx = new InitialContext();
          _log.debug("Using context " + ctx.getEnvironment() + " for " + _spec);
          if (_log.isTraceEnabled())
          {
             _log.trace("setupDestination(" + ctx + ")");
          }

          if (destinationTypeString != null && !destinationTypeString.trim().equals(""))
          {
             _log.debug("Destination type defined as " + destinationTypeString);

             Class<? extends Destination> destinationType;
             if (Topic.class.getName().equals(destinationTypeString))
             {
                destinationType = Topic.class;
                _isTopic = true;
             }
             else
             {
                destinationType = Queue.class;
             }

             _log.debug("Retrieving destination " + destinationName +
                                         " of type " +
                                         destinationType.getName());
             _destination = Util.lookup(ctx, destinationName, destinationType);

          }
          else
          {
             _log.debug("Destination type not defined");
             _log.debug("Retrieving destination " + destinationName +
                                         " of type " +
                                         Destination.class.getName());

             _destination = Util.lookup(ctx, destinationName, AMQDestination.class);
             _isTopic = !(_destination instanceof Queue) ;
          }
       }
       else
       {
          _destination = (AMQDestination)AMQDestination.createDestination(_spec.getDestination(), false);
          
          if (destinationTypeString != null && !destinationTypeString.trim().equals(""))
          {
             _log.debug("Destination type defined as " + destinationTypeString);
             final boolean match ;
             if (Topic.class.getName().equals(destinationTypeString))
             {
                match = (_destination instanceof Topic) ;
                _isTopic = true;
             }
             else
             {
                match = (_destination instanceof Queue) ;
             }
             if (!match)
             {
                throw new ClassCastException("Expected destination of type " + destinationTypeString + " but created destination " + _destination) ;
             }
          }
          else
          {
             _isTopic = !(_destination instanceof Queue) ;
          }
       }

       _log.debug("Got destination " + _destination + " from " + destinationName);
    }
    
    

}
