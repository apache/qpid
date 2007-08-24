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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.qpidity.api.Message;
import org.apache.qpidity.client.util.MessageListener;
import org.apache.qpidity.jms.message.MessageFactory;
import org.apache.qpidity.jms.message.QpidMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p> When asynchronous, upon receive of a message this listener delegate the dispatching to its session.
 * This is for guarantying that asynch messages are sequentially processed within their session.
 * <p> when used synchonously, messages are dispatched to the receiver itself.
 */
public class QpidMessageListener implements MessageListener, Runnable
{
    
    // temp solution
    LinkedBlockingQueue<Message> _queue = new LinkedBlockingQueue<Message>();
    
    /**
     * Used for debugging.
     */
    private static final Logger _logger = LoggerFactory.getLogger(SessionImpl.class);

    /**
     * This message listener consumer
     */
    MessageConsumerImpl _consumer = null;

    //---- constructor
    /**
     * Create a message listener wrapper for a given consumer
     *
     * @param consumer The consumer of this listener
     */
    public QpidMessageListener(MessageConsumerImpl consumer)
    {
        _consumer = consumer;
        Thread t = new Thread(this);
        t.start();
    }
    
    public void run()
    {
        try
        {
            while(true)
            {
                System.out.println("trying to take a message message");
                Message message = _queue.take();
                    
                // to be used with flush
                System.out.println("processing the message");
                _consumer.notifyMessageReceived();
                            
                //convert this message into a JMS one
                QpidMessage jmsMessage = MessageFactory.getQpidMessage(message);
                // if consumer is asynchronous then send this message to its session.
                if( _consumer.getMessageListener() != null )
                {
                    _consumer.getSession().dispatchMessage(_consumer.getMessageActorID(), jmsMessage);
                }
                else
                {
                    // deliver this message to the consumer itself
                    _consumer.onMessage(jmsMessage);
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }

    //---- org.apache.qpidity.MessagePartListener API
    /**
     * Deliver a message to the listener.
     *
     * @param message The message delivered to the listner.
     */
    public void onMessage(Message message)
    {
        System.out.println("Received a message");
        _queue.offer(message);
        System.out.println("Added queue to the message");
    }
}
