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

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.XASession;
import javax.resource.ResourceException;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.transaction.Status;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.XAConnectionImpl;
import org.apache.qpid.ra.QpidResourceAdapter;
import org.apache.qpid.ra.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The message handler
 *
 */
public class QpidMessageHandler extends QpidExceptionHandler implements MessageListener
{
   private static final Logger _log = LoggerFactory.getLogger(QpidMessageHandler.class);

   private MessageConsumer _consumer;

   private MessageEndpoint _endpoint;

   private Session _session;

   private final TransactionManager _tm;

   public QpidMessageHandler(final QpidResourceAdapter ra,
                             final QpidActivationSpec spec,
                             final MessageEndpointFactory endpointFactory,
                             final TransactionManager tm,
                             final Connection connection) throws ResourceException
   {
      super(ra, spec, endpointFactory);      
      this._tm = tm;
      this._connection = connection;
   }
   
   public QpidMessageHandler(final QpidResourceAdapter ra,
                             final QpidActivationSpec spec,
                             final MessageEndpointFactory endpointFactory,
                             final TransactionManager tm) throws ResourceException
   {
       super(ra, spec, endpointFactory);
       this._tm = tm;
   }
   
   public void setup() throws Exception
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setup()");
      }
      
      setupCF();
      setupDestination();
      String selector = _spec.getMessageSelector();
      
      if(_spec.isUseConnectionPerHandler())
      {
          setupConnection();
          _connection.setExceptionListener(this);
      }      
      
      if(isXA())
      {
          _session = _ra.createXASession((XAConnectionImpl)_connection);          
      }
      else
      {
          _session = _ra.createSession((AMQConnection)_connection,
                                      _spec.getAcknowledgeModeInt(),
                                      _spec.isUseLocalTx(),
                                      _spec.getPrefetchLow(),
                                      _spec.getPrefetchHigh());
      }
      // Create the message consumer
      if (_isTopic)
      {
         final Topic topic = (Topic) _destination;
         final String subscriptionName = _spec.getSubscriptionName();
       
         if (_spec.isSubscriptionDurable())
         {
             _consumer = _session.createDurableSubscriber(topic, subscriptionName, selector, false);             
         }
         else
         {
             _consumer = _session.createConsumer(topic, selector) ;             
         }
      }
      else
      {
         final Queue queue = (Queue) _destination;
         _consumer = _session.createConsumer(queue, selector);
      }

      if (isXA())
      {
         final XAResource xaResource = ((XASession)_session).getXAResource() ;
         _endpoint = _endpointFactory.createEndpoint(xaResource);
      }
      else
      {
         _endpoint = _endpointFactory.createEndpoint(null);
      }
      _consumer.setMessageListener(this);
      _connection.start();
      _activated.set(true);
   }

   /**
    * Stop the handler
    */
   public void teardown()
   {
       if (_log.isTraceEnabled())
       {
          _log.trace("teardown()");
       }

      super.teardown();
      
      try
      {
         if (_endpoint != null)
         {
            _endpoint.release();
            _endpoint = null;
         }
      }
      catch (Throwable t)
      {
         _log.debug("Error releasing endpoint " + _endpoint, t);
      }
   }

   public void onMessage(final Message message)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("onMessage(" + Util.asString(message) + ")");
      }

      boolean beforeDelivery = false;

      try
      {
         if (_spec.getTransactionTimeout() > 0 && _tm != null)
         {
            _tm.setTransactionTimeout(_spec.getTransactionTimeout());
         }

         _endpoint.beforeDelivery(QpidActivation.ONMESSAGE);
         beforeDelivery = true;

         if(isXA())
         {
             message.acknowledge();
         }

         ((MessageListener)_endpoint).onMessage(message);

         if (isXA() && (_tm.getTransaction() != null))
         {
            final int status = _tm.getStatus() ;
            final boolean rollback = status == Status.STATUS_MARKED_ROLLBACK
               || status == Status.STATUS_ROLLING_BACK
               || status == Status.STATUS_ROLLEDBACK;
            
            if (rollback)
            {
               _session.recover() ;
            }
         }
         else
         {
            message.acknowledge();
         }

         try
         {
            _endpoint.afterDelivery();
         }
         catch (ResourceException e)
         {
            _log.warn("Unable to call after delivery", e);
            return;
         }
         if (!isXA() && _spec.isUseLocalTx())
         {
            _session.commit();
         }
      }
      catch (Throwable e)
      {
         _log.error("Failed to deliver message", e);
         // we need to call before/afterDelivery as a pair
         if (beforeDelivery)
         {
            try
            {
               _endpoint.afterDelivery();
            }
            catch (ResourceException e1)
            {
               _log.warn("Unable to call after delivery", e);
            }
         }
         if (!isXA() && _spec.isUseLocalTx())
         {
            try
            {
               _session.rollback();
            }
            catch (JMSException e1)
            {
               _log.warn("Unable to roll local transaction back", e1);
            }
         }
         else
         {
            try
            {
               _session.recover() ;
            }
            catch (JMSException e1)
            {
               _log.warn("Unable to recover XA transaction", e1);
            }
         }
      }

   }
   
   public void start() throws Exception
   {
       _deliveryActive.set(true);
       setup();
   }
   
   public void stop()
   {
       _deliveryActive.set(false);
       teardown();
   }

}
