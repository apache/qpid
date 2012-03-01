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

import org.apache.qpid.ra.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The message handler
 *
 */
public class QpidMessageHandler implements MessageListener
{
   /**
    * The logger
    */
   private static final Logger _log = LoggerFactory.getLogger(QpidMessageHandler.class);

   /**
    * The session
    */
   private final Session _session;

   private MessageConsumer _consumer;

   /**
    * The endpoint
    */
   private MessageEndpoint _endpoint;

   private final QpidActivation _activation;

   private boolean _useLocalTx;

   private boolean _transacted;

   private final TransactionManager _tm;

   public QpidMessageHandler(final QpidActivation activation,
                                final TransactionManager tm,
                                final Session session)
   {
      this._activation = activation;
      this._session = session;
      this._tm = tm;
   }

   public void setup() throws Exception
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setup()");
      }

      QpidActivationSpec spec = _activation.getActivationSpec();
      String selector = spec.getMessageSelector();

      // Create the message consumer
      if (_activation.isTopic())
      {
         final Topic topic = (Topic) _activation.getDestination();
         final String subscriptionName = spec.getSubscriptionName();
         if (spec.isSubscriptionDurable())
            _consumer = _session.createDurableSubscriber(topic, subscriptionName, selector, false);
         else
            _consumer = _session.createConsumer(topic, selector) ;
      }
      else
      {
         final Queue queue = (Queue) _activation.getDestination();
         _consumer = _session.createConsumer(queue, selector);
      }

      // Create the endpoint, if we are transacted pass the session so it is enlisted, unless using Local TX
      MessageEndpointFactory endpointFactory = _activation.getMessageEndpointFactory();
      _useLocalTx = _activation.getActivationSpec().isUseLocalTx();
      _transacted = _activation.isDeliveryTransacted() || _useLocalTx ;
      if (_activation.isDeliveryTransacted() && !_activation.getActivationSpec().isUseLocalTx())
      {
         final XAResource xaResource = ((XASession)_session).getXAResource() ;
         _endpoint = endpointFactory.createEndpoint(xaResource);
      }
      else
      {
         _endpoint = endpointFactory.createEndpoint(null);
      }
      _consumer.setMessageListener(this);
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
         if (_activation.getActivationSpec().getTransactionTimeout() > 0 && _tm != null)
         {
            _tm.setTransactionTimeout(_activation.getActivationSpec().getTransactionTimeout());
         }

         _endpoint.beforeDelivery(QpidActivation.ONMESSAGE);
         beforeDelivery = true;

         if(_transacted)
         {
             message.acknowledge();
         }

         ((MessageListener)_endpoint).onMessage(message);

         if (_transacted && (_tm.getTransaction() != null))
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
         if (_useLocalTx)
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
         if (_useLocalTx || !_activation.isDeliveryTransacted())
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

}
