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
import java.text.SimpleDateFormat;

import javax.jms.JMSException;

import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.jms.Session;

/**
 * Provides common functionality that ping clients (the recipients of ping messages) can use. This base class keeps
 * track of the connection used to send pings, provides a convenience method to commit a transaction only when a session
 * to commit on is transactional, keeps track of whether the ping client is pinging to a queue or a topic, provides
 * prompts to the console to terminate brokers before and after commits, in order to test failover functionality, and
 * provides a convience formatter for outputing readable timestamps for pings.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Commit the current transcation on a session.
 * <tr><td> Generate failover promts.
 * <tr><td> Keep track the connection.
 * <tr><td> Keep track of p2p or topic ping type.
 * </table>
 *
 * @todo This base class does not seem particularly usefull and some methods are duplicated in {@link AbstractPingProducer},
 *       consider merging it into that class.
 */
public abstract class AbstractPingClient
{
    private static final Logger _logger = Logger.getLogger(TestPingClient.class);

    /** A convenient formatter to use when time stamping output. */
    protected static final SimpleDateFormat timestampFormatter = new SimpleDateFormat("hh:mm:ss:SS");

    /** Holds the connection to the broker. */
    private AMQConnection _connection;

    /** Flag used to indicate if this is a point to point or pub/sub ping client. */
    private boolean _isPubSub = false;

    /**
     * This flag is used to indicate that the user should be prompted to kill a broker, in order to test
     * failover, immediately before committing a transaction.
     */
    protected boolean _failBeforeCommit = false;

    /**
     * This flag is used to indicate that the user should be prompted to a kill a broker, in order to test
     * failover, immediate after committing a transaction.
     */
    protected boolean _failAfterCommit = false;

    /**
     * Convenience method to commit the transaction on the specified session. If the session to commit on is not
     * a transactional session, this method does nothing.
     *
     * <p/>If the {@link #_failBeforeCommit} flag is set, this will prompt the user to kill the broker before the
     * commit is applied. If the {@link #_failAfterCommit} flag is set, this will prompt the user to kill the broker
     * after the commit is applied.
     *
     * @throws javax.jms.JMSException If the commit fails and then the rollback fails.
     */
    protected void commitTx(Session session) throws JMSException
    {
        if (session.getTransacted())
        {
            try
            {
                if (_failBeforeCommit)
                {
                    _logger.trace("Failing Before Commit");
                    doFailover();
                }

                session.commit();

                if (_failAfterCommit)
                {
                    _logger.trace("Failing After Commit");
                    doFailover();
                }

                _logger.trace("Session Commited.");
            }
            catch (JMSException e)
            {
                _logger.trace("JMSException on commit:" + e.getMessage(), e);

                try
                {
                    session.rollback();
                    _logger.debug("Message rolled back.");
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
     * Prompts the user to terminate the named broker, in order to test failover functionality. This method will block
     * until the user supplied some input on the terminal.
     *
     * @param broker The name of the broker to terminate.
     */
    protected void doFailover(String broker)
    {
        System.out.println("Kill Broker " + broker + " now.");
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
        System.out.println("Kill Broker now.");
        try
        {
            System.in.read();
        }
        catch (IOException e)
        { }

        System.out.println("Continuing.");

    }

    /**
     * Gets the underlying connection that this ping client is running on.
     *
     * @return The underlying connection that this ping client is running on.
     */
    public AMQConnection getConnection()
    {
        return _connection;
    }

    /**
     * Sets the connection that this ping client is using.
     *
     * @param _connection The ping connection.
     */
    public void setConnection(AMQConnection _connection)
    {
        this._connection = _connection;
    }

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
}
