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

import java.util.Collection;
import java.util.Queue;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageListener;

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
 *       to restrict receives on that session to prevent it picking up messages bound for other conversations.
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
    /**
     * Creates a conversation helper on the specified connection with the default sending destination, and listening
     * to the specified receiving destination.
     *
     * @param connection         The connection to build the conversation helper on.
     * @param sendDestination    The default sending destiation for all messages.
     * @param receiveDestination The destination to listen to for incoming messages.
     * @param queueClass         The queue implementation class.
     */
    public ConversationHelper(Connection connection, Destination sendDestination, Destination receiveDestination,
                              Class<? extends Queue> queueClass)
    { }

    /**
     * Sends a message to the default sending location. The correlation id of the message will be assigned by this
     * method, overriding any previously set value.
     *
     * @param message The message to send.
     */
    public void send(Message message)
    { }

    /**
     * Gets the next message in an ongoing conversation. This method may block until such a message is received.
     *
     * @return The next incoming message in the conversation.
     */
    public Message receive()
    {
        return null;
    }

    /**
     * Completes the conversation. Any open transactions are committed. Any correlation id's pertaining to the
     * conversation are no longer valid, and any incoming messages using them will go to the dead letter box.
     */
    public void end()
    { }

    /**
     * Clears the dead letter box, returning all messages that were in it.
     *
     * @return All messages in the dead letter box.
     */
    public Collection<Message> emptyDeadLetterBox()
    {
        return null;
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
        { }
    }
}
