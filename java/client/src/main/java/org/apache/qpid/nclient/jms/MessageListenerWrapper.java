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
package org.apache.qpid.nclient.jms;

import org.apache.qpid.nclient.MessageListener;
import org.apache.qpid.nclient.jms.message.AbstractJMSMessage;
import org.apache.qpid.nclient.jms.message.QpidMessage;
import org.apache.qpidity.api.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a wrapper for the JMS message listener
 */
public class MessageListenerWrapper implements MessageListener
{
    /**
     * Used for debugging.
     */
    private static final Logger _logger = LoggerFactory.getLogger(SessionImpl.class);

    /**
     * This message listener consumer
     */
    MessageConsumerImpl _consumer = null;

    /**
     * The jms listener of this consumer.
     */
    javax.jms.MessageListener _jmsMessageLstener = null;

    //---- constructor
    /**
     * Create a message listener wrapper for a given consumer
     *
     * @param consumer The consumer of this listener
     */
    public MessageListenerWrapper(MessageConsumerImpl consumer)
    {
        _consumer = consumer;
    }

    //---- org.apache.qpid.nclient.MessageListener API
    /**
     * Deliver a message to the listener.
     *
     * @param message The message delivered to the listner.
     */
    public void messageTransfer(Message message)
    {
        try
        {
            // tell the session that a message is in process
            _consumer.getSession().preProcessMessage((QpidMessage) message);
            //TODO build the JMS message form a qpid message
            AbstractJMSMessage jmsMessage = null;
            // If the session is transacted we need to ack the message first
            // This is because a message is associated with its tx only when acked
            if (_consumer.getSession().getTransacted())
            {
                _consumer.getSession().acknowledgeMessage(jmsMessage);
            }
            // The JMS specs says:
            /* The result of a listener throwing a RuntimeException depends on the session�s
            * acknowledgment mode.
            � --- AUTO_ACKNOWLEDGE or DUPS_OK_ACKNOWLEDGE - the message
            * will be immediately redelivered. The number of times a JMS provider will
            * redeliver the same message before giving up is provider-dependent.
            � --- CLIENT_ACKNOWLEDGE - the next message for the listener is delivered.
            * --- Transacted Session - the next message for the listener is delivered.
            *
            * The number of time we try redelivering the message is 0
            **/
            try
            {
                _jmsMessageLstener.onMessage(jmsMessage);
            }
            catch (RuntimeException re)
            {
                // do nothing as this message will not be redelivered
            }
            // If the session has been recovered we then need to redelivered this message
            if (_consumer.getSession().isInRecovery())
            {
                //message.release();
            }
            // Tell the jms Session to ack this message if required
            else if (!_consumer.getSession().getTransacted())
            {
                _consumer.getSession().acknowledgeMessage(jmsMessage);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }
}
