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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.resource.ResourceException;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.XAConnectionImpl;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.ra.QpidResourceAdapter;
import org.apache.qpid.ra.Util;

/**
 * The activation.
 *
 */
public class QpidActivation implements ExceptionListener
{
   /**
    * The logger
    */
   private static final Logger _log = LoggerFactory.getLogger(QpidActivation.class);

   /**
    * The onMessage method
    */
   public static final Method ONMESSAGE;

   /**
    * The resource adapter
    */
   private final QpidResourceAdapter _ra;

   /**
    * The activation spec
    */
   private final QpidActivationSpec _spec;

   /**
    * The message endpoint factory
    */
   private final MessageEndpointFactory _endpointFactory;

   /**
    * Whether delivery is active
    */
   private final AtomicBoolean _deliveryActive = new AtomicBoolean(false);

   /**
    * The destination type
    */
   private boolean _isTopic = false;

   /**
    * Is the delivery transacted
    */
   private boolean _isDeliveryTransacted;

   private Destination _destination;

   /**
    * The connection
    */
   private Connection _connection;

   private final List<QpidMessageHandler> _handlers = new ArrayList<QpidMessageHandler>();

   private AMQConnectionFactory _factory;

   // Whether we are in the failure recovery loop
   private AtomicBoolean _inFailure = new AtomicBoolean(false);

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

   /**
    * Constructor
    *
    * @param ra              The resource adapter
    * @param endpointFactory The endpoint factory
    * @param spec            The activation spec
    * @throws ResourceException Thrown if an error occurs
    */
   public QpidActivation(final QpidResourceAdapter ra,
                            final MessageEndpointFactory endpointFactory,
                            final QpidActivationSpec spec) throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + ra + ", " + endpointFactory + ", " + spec + ")");
      }

      this._ra = ra;
      this._endpointFactory = endpointFactory;
      this._spec = spec;
      try
      {
         _isDeliveryTransacted = endpointFactory.isDeliveryTransacted(QpidActivation.ONMESSAGE);
      }
      catch (Exception e)
      {
         throw new ResourceException(e);
      }
   }

   /**
    * Get the activation spec
    *
    * @return The value
    */
   public QpidActivationSpec getActivationSpec()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getActivationSpec()");
      }

      return _spec;
   }

   /**
    * Get the message endpoint factory
    *
    * @return The value
    */
   public MessageEndpointFactory getMessageEndpointFactory()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getMessageEndpointFactory()");
      }

      return _endpointFactory;
   }

   /**
    * Get whether delivery is transacted
    *
    * @return The value
    */
   public boolean isDeliveryTransacted()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("isDeliveryTransacted()");
      }

      return _isDeliveryTransacted;
   }

   /**
    * Get the work manager
    *
    * @return The value
    */
   public WorkManager getWorkManager()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getWorkManager()");
      }

      return _ra.getWorkManager();
   }

   /**
    * Is the destination a topic
    *
    * @return The value
    */
   public boolean isTopic()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("isTopic()");
      }

      return _isTopic;
   }

   /**
    * Start the activation
    *
    * @throws ResourceException Thrown if an error occurs
    */
   public void start() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("start()");
      }
      _deliveryActive.set(true);
      _ra.getWorkManager().scheduleWork(new SetupActivation());
   }

   /**
    * Stop the activation
    */
   public void stop()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("stop()");
      }

      _deliveryActive.set(false);
      teardown();
   }

   /**
    * Setup the activation
    *
    * @throws Exception Thrown if an error occurs
    */
   protected synchronized void setup() throws Exception
   {
      _log.debug("Setting up " + _spec);
      setupCF();

      setupDestination();
      final AMQConnection amqConnection ;
      final boolean useLocalTx = _spec.isUseLocalTx() ;
      final boolean isXA = _isDeliveryTransacted && !useLocalTx ;

      if (isXA)
      {
         amqConnection = (XAConnectionImpl)_factory.createXAConnection() ;
      }
      else
      {
         amqConnection = (AMQConnection)_factory.createConnection() ;
      }

      amqConnection.setExceptionListener(this) ;

      for (int i = 0; i < _spec.getMaxSession(); i++)
      {
         Session session = null;

         try
         {
            if (isXA)
            {
               session = _ra.createXASession((XAConnectionImpl)amqConnection) ;
            }
            else
            {
               session = _ra.createSession((AMQConnection)amqConnection,
                     _spec.getAcknowledgeModeInt(),
                     useLocalTx,
                     _spec.getPrefetchLow(),
                     _spec.getPrefetchHigh());
            }

            _log.debug("Using session " + Util.asString(session));
            QpidMessageHandler handler = new QpidMessageHandler(this, _ra.getTM(), session);
            handler.setup();
            _handlers.add(handler);
         }
         catch (Exception e)
         {
            try
            {
               amqConnection.close() ;
            }
            catch (Exception e2)
            {
               _log.trace("Ignored error closing connection", e2);
            }

            throw e;
         }
      }
      amqConnection.start() ;
      this._connection = amqConnection ;

      _log.debug("Setup complete " + this);
   }

   /**
    * Teardown the activation
    */
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

      for (QpidMessageHandler handler : _handlers)
      {
         handler.teardown();
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

   public Destination getDestination()
   {
      return _destination;
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
            //_destination = (Destination)ctx.lookup(destinationName);

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
         _destination = (AMQDestination)AMQDestination.createDestination(_spec.getDestination());
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

   /**
    * Get a string representation
    *
    * @return The value
    */
   @Override
   public String toString()
   {
      StringBuffer buffer = new StringBuffer();
      buffer.append(QpidActivation.class.getName()).append('(');
      buffer.append("spec=").append(_spec.getClass().getName());
      buffer.append(" mepf=").append(_endpointFactory.getClass().getName());
      buffer.append(" active=").append(_deliveryActive.get());
      if (_spec.getDestination() != null)
      {
         buffer.append(" destination=").append(_spec.getDestination());
      }
      buffer.append(" transacted=").append(_isDeliveryTransacted);
      buffer.append(')');
      return buffer.toString();
   }

   public void onException(final JMSException jmse)
   {
      handleFailure(jmse) ;
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
         _log.info("awaiting topic/queue creation " + getActivationSpec().getDestination());
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
                  _log.info("awaiting topic/queue creation " + getActivationSpec().getDestination());
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
   private boolean doesNotExist(final Throwable failure)
   {
      return (failure instanceof AMQException) && (((AMQException)failure).getErrorCode() == AMQConstant.NOT_FOUND) ;
   }

   /**
    * Handles the setup
    */
   private class SetupActivation implements Work
   {
      public void run()
      {
         try
         {
            setup();
         }
         catch (Throwable t)
         {
            handleFailure(t);
         }
      }

      public void release()
      {
      }
   }
}
