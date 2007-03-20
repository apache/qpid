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
package org.apache.qpid.util;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.*;

/**
 * A conversation helper, uses a message correlation id pattern to match up sent and received messages as a conversation
 * over JMS messaging. Incoming message traffic is divided up by correlation id. Each id has a queue (behaviour dependant
 * on the queue implementation). Clients of this de-multiplexer can wait on messages, defined by message correlation ids.
 * The correlating listener is a message listener, and can therefore be attached to a MessageConsumer which is consuming
 * from a queue or topic.
 *
 * <p/>One use of the correlating listener is to act as a conversation synchronizer where multiple threads are carrying
 * out conversations over a multiplexed messaging route. This can be usefull, as JMS sessions are not multi-threaded.
 * Setting up the correlating listener with synchronous queues will allow these threads to be written in a synchronous
 * style, but with their execution order governed by the asynchronous message flow. For example, something like the
 * following code could run a multi-threaded conversation (the conversation methods can be called many times in
 * parallel):
 *
 * <p/><pre>
 * MessageListener conversation = new ConversationHelper(java.util.concurrent.LinkedBlockingQueue.class),
 *                                                       sendDesitination, replyDestination);
 *
 * initiateConversation()
 * {
 *  try {
 *   // Exchange greetings.
 *   conversation.send(conversation.getSession().createTextMessage("Hello."));
 *   Message greeting = conversation.receive();
 *
 *   // Exchange goodbyes.
 *   conversation.send(conversation.getSession().createTextMessage("Goodbye."));
 *   Message goodbye = conversation.receive();
 *  } finally {
 *   conversation.end();
 *  }
 * }
 *
 * respondToConversation()
 * {
 *   try {
 *   // Exchange greetings.
 *   Message greeting = conversation.receive();
 *   conversation.send(conversation.getSession().createTextMessage("Hello."));
 *
 *   // Exchange goodbyes.
 *   Message goodbye = conversation.receive();
 *   conversation.send(conversation.getSession().createTextMessage("Goodbye."));
 *  } finally {
 *   conversation.end();
 *  }
 * }
 *
 * </pre>
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><th> Associate messages to a conversation using correlation ids.
 * <tr><td> Auto manage sessions for conversations.
 * <tr><td> Store messages not in conversation in dead letter box.
 * </table>
 *
 * @todo Non-transactional, can use shared session. Transactional, must have session per-thread. Session pool? In
 *       transactional mode, commits must happen before receiving, or no replies will come in. (unless there were some
 *       pending on the queue?). Also, having received on a particular session, must ensure that session is used for all
 *       subsequent sends and receive at least until the transaction is committed. So a message selector must be used
 *       to restrict receives on that session to prevent it picking up messages bound for other conversations. Or use
 *       a temporary response queue, with only that session listening to it.
 *
 * @todo Want something convenient that hides many details. Write out some example use cases to get the best feel for
 *       it. Pass in connection, send destination, receive destination. Provide endConvo, send, receive
 *       methods. Bind corrId, session etc. on thread locals. Clean on endConvo. Provide deadLetter box, that
 *       uncorrelated or late messages go in. Provide time-out on wait methods, and global time-out.
 *       PingPongProducer provides a good use-case example (sends messages, waits for replies).
 *
 * @todo New correlationId on every send? or correlation id per conversation? or callers choice.
 */
public class ConversationHelper
{
    /** Holds a map from correlation id's to queues. */
    private Map<Long, BlockingQueue<Message>> idsToQueues = new HashMap<Long, BlockingQueue<Message>>();

    private Session session;
    private MessageProducer producer;
    private MessageConsumer consumer;

    Class<? extends BlockingQueue> queueClass;

    BlockingQueue<Message> deadLetterBox = new LinkedBlockingQueue<Message>();

    ThreadLocal<PerThreadSettings> threadLocals =
        new ThreadLocal<PerThreadSettings>()
        {
            protected PerThreadSettings initialValue()
            {
                PerThreadSettings settings = new PerThreadSettings();
                settings.conversationId = conversationIdGenerator.getAndIncrement();

                return settings;
            }
        };

    /** Generates new coversation id's as needed. */
    AtomicLong conversationIdGenerator = new AtomicLong();

    /**
     * Creates a conversation helper on the specified connection with the default sending destination, and listening
     * to the specified receiving destination.
     *
     * @param connection         The connection to build the conversation helper on.
     * @param sendDestination    The default sending destiation for all messages.
     * @param receiveDestination The destination to listen to for incoming messages.
     * @param queueClass         The queue implementation class.
     *
     * @throws JMSException All undelying JMSExceptions are allowed to fall through.
     */
    public ConversationHelper(Connection connection, Destination sendDestination, Destination receiveDestination,
                              Class<? extends BlockingQueue> queueClass) throws JMSException
    {
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        producer = session.createProducer(sendDestination);
        consumer = session.createConsumer(receiveDestination);

        consumer.setMessageListener(new Receiver());

        this.queueClass = queueClass;
    }

    /**
     * Sends a message to the default sending location. The correlation id of the message will be assigned by this
     * method, overriding any previously set value.
     *
     * @param message The message to send.
     *
     * @throws JMSException All undelying JMSExceptions are allowed to fall through.
     */
    public void send(Message message) throws JMSException
    {
        PerThreadSettings settings = threadLocals.get();
        long conversationId = settings.conversationId;
        message.setJMSCorrelationID(Long.toString(conversationId));

        // Ensure that the reply queue for this conversation exists.
        initQueueForId(conversationId);

        producer.send(message);
    }

    /**
     * Ensures that the reply queue for a conversation exists.
     *
     * @param conversationId The conversation correlation id.
     */
    private void initQueueForId(long conversationId)
    {
        if (!idsToQueues.containsKey(conversationId))
        {
            idsToQueues.put(conversationId, ReflectionUtils.<BlockingQueue>newInstance(queueClass));
        }
    }

    /**
     * Gets the next message in an ongoing conversation. This method may block until such a message is received.
     *
     * @return The next incoming message in the conversation.
     */
    public Message receive()
    {
        PerThreadSettings settings = threadLocals.get();
        long conversationId = settings.conversationId;

        // Ensure that the reply queue for this conversation exists.
        initQueueForId(conversationId);

        BlockingQueue<Message> queue = idsToQueues.get(conversationId);

        try
        {
            return queue.take();
        }
        catch (InterruptedException e)
        {
            return null;
        }
    }

    /**
     * Gets many messages in an ongoing conversation. If a limit is specified, then once that many messages are
     * received they will be returned. If a timeout is specified, then all messages up to the limit, received within
     * that timespan will be returned.
     *
     * @param num     The number of messages to receive, or all if this is less than 1.
     * @param timeout The timeout in milliseconds to receive the messages in, or forever if this is less than 1.
     *
     * @return All messages received within the count limit and the timeout.
     */
    public Collection<Message> receiveAll(int num, long timeout)
    {
        Collection<Message> result = new ArrayList<Message>();

        for (int i = 0; i < num; i++)
        {
            result.add(receive());
        }

        return result;
    }

    /**
     * Completes the conversation. Any open transactions are committed. Any correlation id's pertaining to the
     * conversation are no longer valid, and any incoming messages using them will go to the dead letter box.
     */
    public void end()
    {
        // Ensure that the thread local for the current thread is cleaned up.
        PerThreadSettings settings = threadLocals.get();
        long conversationId = settings.conversationId;
        threadLocals.remove();

        // Ensure that its queue is removed from the queue map.
        BlockingQueue<Message> queue = idsToQueues.remove(conversationId);

        // Move any outstanding messages on the threads conversation id into the dead letter box.
        queue.drainTo(deadLetterBox);
    }

    /**
     * Clears the dead letter box, returning all messages that were in it.
     *
     * @return All messages in the dead letter box.
     */
    public Collection<Message> emptyDeadLetterBox()
    {
        Collection<Message> result = new ArrayList<Message>();
        deadLetterBox.drainTo(result);

        return result;
    }

    /**
     * Implements the message listener for this conversation handler.
     */
    protected class Receiver implements MessageListener
    {
        /**
         * Handles all incoming messages in the ongoing conversations. These messages are split up by correaltion id
         * and placed into queues.
         *
         * @param message The incoming message.
         */
        public void onMessage(Message message)
        {
            try
            {
                Long conversationId = Long.parseLong(message.getJMSCorrelationID());

                // Find the converstaion queue to place the message on. If there is no conversation for the message id,
                // the the dead letter box queue is used.
                BlockingQueue<Message> queue = idsToQueues.get(conversationId);
                queue = (queue == null) ? deadLetterBox : queue;

                queue.put(message);
            }
            catch (JMSException e)
            {
                throw new RuntimeException(e);
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    protected class PerThreadSettings
    {
        /** Holds the correlation id for the current threads conversation. */
        long conversationId;
    }

    public Session getSession()
    {
        return session;
    }
}
