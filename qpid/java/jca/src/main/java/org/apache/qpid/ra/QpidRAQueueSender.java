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

package org.apache.qpid.ra;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QpidRAQueueSender.
 *
 */
public class QpidRAQueueSender extends QpidRAMessageProducer implements QueueSender
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRAQueueSender.class);

   /**
    * Create a new wrapper
    * @param producer the producer
    * @param session the session
    */
   public QpidRAQueueSender(final QueueSender producer, final QpidRASessionImpl session)
   {
      super(producer, session);

      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + Util.asString(producer) + ", " + session + ")");
      }
   }

   /**
    * Get queue
    * @return The queue
    * @exception JMSException Thrown if an error occurs
    */
   public Queue getQueue() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getQueue()");
      }

      return ((QueueSender)_producer).getQueue();
   }

   /**
    * Send message
    * @param destination The destination
    * @param message The message
    * @param deliveryMode The delivery mode
    * @param priority The priority
    * @param timeToLive The time to live
    * @exception JMSException Thrown if an error occurs
    */
   public void send(final Queue destination,
                    final Message message,
                    final int deliveryMode,
                    final int priority,
                    final long timeToLive) throws JMSException
   {
      _session.lock();
      try
      {
         if (_log.isTraceEnabled())
         {
            _log.trace("send " + this +
                                           " destination=" +
                                           destination +
                                           " message=" +
                                           Util.asString(message) +
                                           " deliveryMode=" +
                                           deliveryMode +
                                           " priority=" +
                                           priority +
                                           " ttl=" +
                                           timeToLive);
         }

         checkState();
         _producer.send(destination, message, deliveryMode, priority, timeToLive);

         if (_log.isTraceEnabled())
         {
            _log.trace("sent " + this + " result=" + Util.asString(message));
         }
      }
      finally
      {
         _session.unlock();
      }
   }

   /**
    * Send message
    * @param destination The destination
    * @param message The message
    * @exception JMSException Thrown if an error occurs
    */
   public void send(final Queue destination, final Message message) throws JMSException
   {
      _session.lock();
      try
      {
         if (_log.isTraceEnabled())
         {
            _log.trace("send " + this + " destination=" + destination + " message=" + Util.asString(message));
         }

         checkState();
         _producer.send(destination, message);

         if (_log.isTraceEnabled())
         {
            _log.trace("sent " + this + " result=" + Util.asString(message));
         }
      }
      finally
      {
         _session.unlock();
      }
   }
}
