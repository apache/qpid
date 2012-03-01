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
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper for a topic subscriber
 *
 */
public class QpidRATopicSubscriber extends QpidRAMessageConsumer implements TopicSubscriber
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRATopicSubscriber.class);

   /**
    * Create a new wrapper
    * @param consumer the topic subscriber
    * @param session the session
    */
   public QpidRATopicSubscriber(final TopicSubscriber consumer, final QpidRASessionImpl session)
   {
      super(consumer, session);

      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + Util.asString(consumer) + ", " + session + ")");
      }
   }

   /**
    * Get the no local value
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public boolean getNoLocal() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getNoLocal()");
      }

      checkState();
      return ((TopicSubscriber)_consumer).getNoLocal();
   }

   /**
    * Get the topic
    * @return The topic
    * @exception JMSException Thrown if an error occurs
    */
   public Topic getTopic() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getTopic()");
      }

      checkState();
      return ((TopicSubscriber)_consumer).getTopic();
   }
}
