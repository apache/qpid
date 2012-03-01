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

import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueBrowser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QpidRAQueueBrowser.
 *
 */
public class QpidRAQueueBrowser implements QueueBrowser
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRAQueueBrowser.class);

   /** The wrapped queue browser */
   protected QueueBrowser _browser;

   /** The session for this consumer */
   protected QpidRASessionImpl _session;

   /** The closed flag */
   private AtomicBoolean _isClosed = new AtomicBoolean();

   /**
    * Create a new wrapper
    * @param browser the browser
    * @param session the session
    */
   public QpidRAQueueBrowser(final QueueBrowser browser, final QpidRASessionImpl session)
   {
      _browser = browser;
      _session = session;

      if (_log.isTraceEnabled())
      {
         _log.trace("new QpidRAQueueBrowser " + this +
                                            " browser=" +
                                            Util.asString(browser) +
                                            " session=" +
                                            session);
      }
   }

   /**
    * Close
    * @exception JMSException Thrown if an error occurs
    */
   public void close() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("close " + this);
      }

      if (!_isClosed.getAndSet(true))
      {
         try
         {
            _browser.close();
         }
         finally
         {
            _session.removeQueueBrowser(this);
         }
      }
   }

   /**
    * Get the queue associated with this browser.
    * @return the associated queue.
    */
   public Queue getQueue()
      throws JMSException
   {
      return _browser.getQueue();
   }

   /**
    * Get the message selector associated with this browser.
    * @return the associated message selector.
    */
   public String getMessageSelector()
      throws JMSException
   {
      return _browser.getMessageSelector();
   }

   /**
    * Get an enumeration for the current browser.
    * @return The enumeration.
    */
   public Enumeration getEnumeration()
      throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getEnumeration " + this + " browser=" + Util.asString(_browser));
      }

      _session.lock();
      try
      {
         _session.checkState();
         return _browser.getEnumeration();
      }
      finally
      {
         _session.unlock();
      }
   }
}
