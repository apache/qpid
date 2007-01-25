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
package org.apache.qpid.ping;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.*;
import javax.jms.Connection;
import javax.jms.Message;

import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQNoConsumersException;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.message.TestMessageFactory;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jms.Session;

/**
 * Provides common functionality that ping producers (the senders of ping messages) can use. This base class keeps
 * track of the connection used to send pings; provides a convenience method to commit a transaction only when a session
 * to commit on is transactional; keeps track of whether the ping client is pinging to a queue or a topic; provides
 * prompts to the console to terminate brokers before and after commits, in order to test failover functionality;
 * requires sub-classes to implement a ping loop, that this provides a run loop to repeatedly call; provides a
 * default shutdown hook to cleanly terminate the run loop; keeps track of the destinations to send pings to;
 * provides a convenience method to generate short pauses; and provides a convience formatter for outputing readable
 * timestamps for pings.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Commit the current transcation on a session.
 * <tr><td> Generate failover promts.
 * <tr><td> Keep track the connection.
 * <tr><td> Keep track of p2p or topic ping type.
 * <tr><td> Call ping loop to repeatedly send pings.
 * <tr><td> Provide a shutdown hook.
 * <tr><td> Generate short pauses.
 * </table>
 *
 * @todo Destination count versus list of desintations is redundant. Use _destinions.size() to get the count and
 *       use a list of 1 destination when only 1 is needed. It is only important to distinguish when 1 destination
 *       is shared between multiple ping producers on the same JVM or if each ping producer has its own single
 *       destination.
 *
 * @todo Timestamp messages in nanos, not millis. Millis seems to have bad resolution, at least on windows.
 */
public abstract class AbstractPingProducer implements Runnable, ExceptionListener
{
    private static final Logger _logger = Logger.getLogger(AbstractPingProducer.class);

    /** Flag used to indicate if this is a point to point or pub/sub ping client. */
    private boolean _isPubSub = false;

    /** A convenient formatter to use when time stamping output. */
    protected static final DateFormat timestampFormatter = new SimpleDateFormat("hh:mm:ss:SS");

    /**
     * This id generator is used to generate ids to append to the queue name to ensure that queues can be unique when
     * creating multiple ping producers in the same JVM.
     */
    private static AtomicInteger _queueSequenceID = new AtomicInteger();

    /** Used to tell the ping loop when to terminate, it only runs while this is true. */
    protected boolean _publish = true;

    /** Holds the connection to the broker. */
    private Connection _connection;

    /** Holds the producer session, needed to create ping messages. */
    private Session _producerSession;

    /** Holds the number of destinations that this ping producer will send pings to, defaulting to a single destination. */
    protected int _destinationCount = 1;

    /** Holds the set of destiniations that this ping producer pings. */
    private List<Destination> _destinations = new ArrayList<Destination>();

    /** Holds the message producer to send the pings through. */
    protected org.apache.qpid.jms.MessageProducer _producer;

    /** Flag used to indicate that the user should be prompted to terminate a broker, to test failover before a commit. */
    protected boolean _failBeforeCommit = false;

    /** Flag used to indicate that the user should be prompted to terminate a broker, to test failover after a commit. */
    protected boolean _failAfterCommit = false;

    /** Flag used to indicate that the user should be prompted to terminate a broker, to test failover before a send. */
    protected boolean _failBeforeSend = false;

    /** Flag used to indicate that the user should be prompted to terminate a broker, to test failover after a send. */
    protected boolean _failAfterSend = false;

    /** Flag used to indicate that failover prompting should only be done on the first commit, not on every commit. */
    protected boolean _failOnce = true;

    /** Holds the number of sends that should be performed in every transaction when using transactions. */
    protected int _txBatchSize = 1;

    /**
     * Sets or clears the pub/sub flag to indiciate whether this client is pinging a queue or a topic.
     *
     * @param pubsub <tt>true</tt> if this client is pinging a topic, <tt>false</tt> if it is pinging a queue.
     */
    public void setPubSub(boolean pubsub)
    {
        _isPubSub = pubsub;
    }

    /**
     * Checks whether this client is a p2p or pub/sub ping client.
     *
     * @return <tt>true</tt> if this client is pinging a topic, <tt>false</tt> if it is pinging a queue.
     */
    public boolean isPubSub()
    {
        return _isPubSub;
    }

    /**
     * Convenience method for a short pause.
     *
     * @param sleepTime The time in milliseconds to pause for.
     */
    public static void pause(long sleepTime)
    {
        if (sleepTime > 0)
        {
            try
            {
                Thread.sleep(sleepTime);
            }
            catch (InterruptedException ie)
            { }
        }
    }

    /**
     * Implementations should provide this method to perform a single ping cycle (which may send many messages). The
     * run loop will repeatedly call this method until the publish flag is set to false.
     */
    public abstract void pingLoop();

    /**
     * Generates a test message of the specified size, with the specified reply-to destination and persistence flag.
     *
     * @param replyQueue  The reply-to destination for the message.
     * @param messageSize The desired size of the message in bytes.
     * @param persistent  <tt>true</tt> if the message should use persistent delivery, <tt>false</tt> otherwise.
     *
     * @return A freshly generated test message.
     *
     * @throws JMSException All underlying JMSException are allowed to fall through.
     */
    public ObjectMessage getTestMessage(Destination replyQueue, int messageSize, boolean persistent) throws JMSException
    {
        ObjectMessage msg = TestMessageFactory.newObjectMessage(_producerSession, replyQueue, messageSize, persistent);
        // Timestamp the message.
        msg.setLongProperty("timestamp", System.currentTimeMillis());

        return msg;
    }

    /**
     * Stops the ping loop by clearing the publish flag. The current loop will complete before it notices that this
     * flag has been cleared.
     */
    public void stop()
    {
        _publish = false;
    }

    /**
     * Implements a ping loop that repeatedly pings until the publish flag becomes false.
     */
    public void run()
    {
        // Keep running until the publish flag is cleared.
        while (_publish)
        {
            pingLoop();
        }
    }

    /**
     * Callback method, implementing ExceptionListener. This should be registered to listen for exceptions on the
     * connection, this clears the publish flag which in turn will halt the ping loop.
     *
     * @param e The exception that triggered this callback method.
     */
    public void onException(JMSException e)
    {
        _publish = false;
        _logger.debug("There was a JMSException: " + e.getMessage(), e);
    }

    /**
     * Gets a shutdown hook that will cleanly shut this down when it is running the ping loop. This can be registered
     * with the runtime system as a shutdown hook.
     *
     * @return A shutdown hook for the ping loop.
     */
    public Thread getShutdownHook()
    {
        return new Thread(new Runnable()
            {
                public void run()
                {
                    stop();
                }
            });
    }

    /**
     * Gets the underlying connection that this ping client is running on.
     *
     * @return The underlying connection that this ping client is running on.
     */
    public Connection getConnection()
    {
        return _connection;
    }

    /**
     * Sets the connection that this ping client is using.
     *
     * @param connection The ping connection.
     */
    public void setConnection(Connection connection)
    {
        this._connection = connection;
    }

    /**
     * Gets the producer session that the ping client is using to send pings on.
     *
     * @return The producer session that the ping client is using to send pings on.
     */
    public Session getProducerSession()
    {
        return _producerSession;
    }

    /**
     * Keeps track of the producer session that the ping client is using to send pings on.
     *
     * @param session The producer session that the ping client is using to send pings on.
     */
    public void setProducerSession(Session session)
    {
        this._producerSession = session;
    }

    /**
     * Gets the number of destinations that this ping client is sending to.
     *
     * @return The number of destinations that this ping client is sending to.
     */
    public int getDestinationsCount()
    {
        return _destinationCount;
    }

    /**
     * Sets the number of destination that this ping client should send to.
     *
     * @param count The number of destination that this ping client should send to.
     *
     * @deprectaed Use _destinations.size() instead.
     */
    public void setDestinationsCount(int count)
    {
        this._destinationCount = count;
    }

    /**
     * Commits the transaction on the producer session.
     *
     * @throws JMSException All underlying JMSExceptions are allowed to fall through.
     *
     * @deprecated Use the commitTx(Session session) method instead, to explicitly specify which session is being
     *             committed. This makes it more obvious what is going on.
     */
    protected void commitTx() throws JMSException
    {
        commitTx(getProducerSession());
    }

    /**
     * Creates the specified number of destinations to send pings to. Topics or Queues will be created depending on
     * the value of the {@link #_isPubSub} flag.
     *
     * @param count The number of ping destinations to create.
     */
    protected void createDestinations(int count)
    {
        // Create the desired number of ping destinations.
        for (int i = 0; i < count; i++)
        {
            AMQDestination destination = null;

            // Check if this is a pub/sub pinger, in which case create topics.
            if (isPubSub())
            {
                AMQShortString name =
                    new AMQShortString("AMQTopic_" + _queueSequenceID.incrementAndGet() + "_" + System.currentTimeMillis());
                destination = new AMQTopic(name);
            }
            // Otherwise this is a p2p pinger, in which case create queues.
            else
            {
                AMQShortString name =
                    new AMQShortString("AMQQueue_" + _queueSequenceID.incrementAndGet() + "_" + System.currentTimeMillis());
                destination = new AMQQueue(name, name, false, false, false);
            }

            _destinations.add(destination);
        }
    }

    /**
     * Returns the destination from the destinations list with the given index.
     *
     * @param index The index of the destination to get.
     *
     * @return Destination with the given index.
     */
    protected Destination getDestination(int index)
    {
        return _destinations.get(index);
    }

    /**
     * Convenience method to commit the transaction on the specified session. If the session to commit on is not
     * a transactional session, this method does nothing (unless the failover after send flag is set).
     *
     * <p/>If the {@link #_failAfterSend} flag is set, this will prompt the user to kill the broker before the commit
     * is applied. This flag applies whether the pinger is transactional or not.
     *
     * <p/>If the {@link #_failBeforeCommit} flag is set, this will prompt the user to kill the broker before the
     * commit is applied. If the {@link #_failAfterCommit} flag is set, this will prompt the user to kill the broker
     * after the commit is applied. These flags will only apply if using a transactional pinger.
     *
     * @throws javax.jms.JMSException If the commit fails and then the rollback fails.
     *
     * @todo Consider moving the fail after send logic into the send method. It is confusing to have it in this commit
     *       method, because commits only apply to transactional pingers, but fail after send applied to transactional
     *       and non-transactional alike.
     */
    protected void commitTx(Session session) throws JMSException
    {
        _logger.trace("Batch time reached");
        if (_failAfterSend)
        {
            _logger.trace("Batch size reached");
            if (_failOnce)
            {
                _failAfterSend = false;
            }

            _logger.trace("Failing After Send");
            doFailover();
        }

        if (session.getTransacted())
        {
            try
            {
                if (_failBeforeCommit)
                {
                    if (_failOnce)
                    {
                        _failBeforeCommit = false;
                    }

                    _logger.trace("Failing Before Commit");
                    doFailover();
                }

                session.commit();

                if (_failAfterCommit)
                {
                    if (_failOnce)
                    {
                        _failAfterCommit = false;
                    }

                    _logger.trace("Failing After Commit");
                    doFailover();
                }

                _logger.trace("Session Commited.");
            }
            catch (JMSException e)
            {
                _logger.trace("JMSException on commit:" + e.getMessage(), e);

                // Warn that the bounce back client is not available.
                if (e.getLinkedException() instanceof AMQNoConsumersException)
                {
                    _logger.debug("No consumers on queue.");
                }

                try
                {
                    session.rollback();
                    _logger.trace("Message rolled back.");
                }
                catch (JMSException jmse)
                {
                    _logger.trace("JMSE on rollback:" + jmse.getMessage(), jmse);

                    // Both commit and rollback failed. Throw the rollback exception.
                    throw jmse;
                }
            }
        }
    }

    /**
     * Sends the specified message to the default destination of the ping producer.
     *
     * @param message The message to send.
     *
     * @throws JMSException All underlying JMSExceptions are allowed to fall through.
     */
    protected void sendMessage(Message message) throws JMSException
    {
        sendMessage(null, message);
    }

    /**
     * Sends the message to the specified destination. If the destination is null, it gets sent to the default destination
     * of the ping producer. If an explicit destination is set, this overrides the default.
     *
     * @param destination The destination to send to.
     * @param message     The message to send.
     *
     * @throws JMSException All underlying JMSExceptions are allowed to fall through.
     */
    protected void sendMessage(Destination destination, Message message) throws JMSException
    {
        if (_failBeforeSend)
        {
            if (_failOnce)
            {
                _failBeforeSend = false;
            }

            _logger.trace("Failing Before Send");
            doFailover();
        }

        if (destination == null)
        {
            _producer.send(message);
        }
        else
        {
            _producer.send(destination, message);
        }
    }

    /**
     * Prompts the user to terminate the named broker, in order to test failover functionality. This method will block
     * until the user supplied some input on the terminal.
     *
     * @param broker The name of the broker to terminate.
     */
    protected void doFailover(String broker)
    {
        System.out.println("Kill Broker " + broker + " now then press return");
        try
        {
            System.in.read();
        }
        catch (IOException e)
        { }

        System.out.println("Continuing.");
    }

    /**
     * Prompts the user to terminate the broker, in order to test failover functionality. This method will block
     * until the user supplied some input on the terminal.
     */
    protected void doFailover()
    {
        System.out.println("Kill Broker now then press return");
        try
        {
            System.in.read();
        }
        catch (IOException e)
        { }

        System.out.println("Continuing.");
    }
}
