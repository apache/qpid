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
import javax.jms.Queue;
import javax.jms.QueueReceiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper for a queue receiver
 *
 */
public class QpidRAQueueReceiver extends QpidRAMessageConsumer implements QueueReceiver
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRAQueueReceiver.class);

   /**
    * Create a new wrapper
    * @param consumer the queue receiver
    * @param session the session
    */
   public QpidRAQueueReceiver(final QueueReceiver consumer, final QpidRASessionImpl session)
   {
      super(consumer, session);

      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + Util.asString(consumer) + ", " + session + ")");
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

      checkState();
      return ((QueueReceiver)_consumer).getQueue();
   }
}
