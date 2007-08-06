/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpidity.jms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.qpidity.api.Message;
import org.apache.qpidity.client.MessageListener;

/**
 * This listener idspatches messaes to its browser.
 */
public class QpidBrowserListener implements MessageListener
{
    /**
     * Used for debugging.
     */
    private static final Logger _logger = LoggerFactory.getLogger(SessionImpl.class);

    /**
     * This message listener's browser.
     */
    QueueBrowserImpl _browser = null;

      //---- constructor
    /**
     * Create a message listener wrapper for a given browser
     *
     * @param browser The browser of this listener
     */
    public QpidBrowserListener(QueueBrowserImpl browser)
    {
        _browser = browser;
    }

    //---- org.apache.qpidity.MessagePartListener API
    /**
     * Deliver a message to the listener.
     *
     * @param message The message delivered to the listner.
     */
    public void onMessage(Message message)
    {
        try
        {
            //convert this message into a JMS one
            javax.jms.Message jmsMessage = null; // todo
            _browser.receiveMessage(jmsMessage);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }
}
