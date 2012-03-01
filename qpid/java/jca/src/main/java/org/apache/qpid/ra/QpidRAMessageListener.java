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

import javax.jms.Message;
import javax.jms.MessageListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper for a message listener
 */
public class QpidRAMessageListener implements MessageListener
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRAMessageListener.class);

   /** The message listener */
   private final MessageListener _listener;

   /** The consumer */
   private final QpidRAMessageConsumer _consumer;

   /**
    * Create a new wrapper
    * @param listener the listener
    * @param consumer the consumer
    */
   public QpidRAMessageListener(final MessageListener listener, final QpidRAMessageConsumer consumer)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + listener + ", " + consumer + ")");
      }

      this._listener = listener;
      this._consumer = consumer;
   }

   /**
    * On message
    * @param message The message
    */
   public void onMessage(Message message)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("onMessage(" + Util.asString(message) + ")");
      }

      message = _consumer.wrapMessage(message);
      _listener.onMessage(message);
   }
}
