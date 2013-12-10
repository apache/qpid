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
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper for a message
 *
 */
public class QpidRATextMessage extends QpidRAMessage implements TextMessage
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRATextMessage.class);

   /**
    * Create a new wrapper
    * @param message the message
    * @param session the session
    */
   public QpidRATextMessage(final TextMessage message, final QpidRASessionImpl session)
   {
      super(message, session);

      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + Util.asString(message) + ", " + session + ")");
      }
   }

   /**
    * Get text
    * @return The text
    * @exception JMSException Thrown if an error occurs
    */
   public String getText() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getText()");
      }

      return ((TextMessage)_message).getText();
   }

   /**
    * Set text
    * @param string The text
    * @exception JMSException Thrown if an error occurs
    */
   public void setText(final String string) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setText(" + string + ")");
      }

      ((TextMessage)_message).setText(string);
   }
}
