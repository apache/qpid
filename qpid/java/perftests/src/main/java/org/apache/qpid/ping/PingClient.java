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

import java.util.List;

import javax.jms.Destination;

import org.apache.qpid.requestreply.PingPongProducer;

/**
 * PingClient is a {@link PingPongProducer} that does not need a {@link org.apache.qpid.requestreply.PingPongBouncer}
 * to send replies to its pings. It simply listens to its own ping destinations, rather than seperate reply queues.
 * It is an all in one ping client, that produces and consumes its own pings.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Create a ping pong producer that listens to its own pings <td> {@link PingPongProducer}
 * </table>
 */
public class PingClient extends PingPongProducer
{
    private static int _pingClientCount;

    /**
     * Creates a ping producer with the specified parameters, of which there are many. See their individual comments
     * for details. This constructor creates ping pong producer but de-registers its reply-to destination message
     * listener, and replaces it by listening to all of its ping destinations.
     *
     * @param brokerDetails    The URL of the broker to send pings to.
     * @param username         The username to log onto the broker with.
     * @param password         The password to log onto the broker with.
     * @param virtualpath      The virtual host name to use on the broker.
     * @param destinationName  The name (or root where multiple destinations are used) of the desitination to send
     *                         pings to.
     * @param selector         The selector to filter replies with.
     * @param transacted       Indicates whether or not pings are sent and received in transactions.
     * @param persistent       Indicates whether pings are sent using peristent delivery.
     * @param messageSize      Specifies the size of ping messages to send.
     * @param verbose          Indicates that information should be printed to the console on every ping.
     * @param afterCommit      Indicates that the user should be promted to terminate a broker after commits to test failover.
     * @param beforeCommit     Indicates that the user should be promted to terminate a broker before commits to test failover.
     * @param afterSend        Indicates that the user should be promted to terminate a broker after sends to test failover.
     * @param beforeSend       Indicates that the user should be promted to terminate a broker before sends to test failover.
     * @param failOnce         Indicates that the failover testing behaviour should only happen on the first commit, not all.
     * @param txBatchSize      Specifies the number of pings to send in each transaction.
     * @param noOfDestinations The number of destinations to ping. Must be 1 or more.
     * @param rate             Specified the number of pings per second to send. Setting this to 0 means send as fast as
     *                         possible, with no rate restriction.
     * @param pubsub           True to ping topics, false to ping queues.
     * @param unique           True to use unique destinations for each ping pong producer, false to share.
     *
     * @throws Exception Any exceptions are allowed to fall through.
     */
    public PingClient(String brokerDetails, String username, String password, String virtualpath, String destinationName,
                      String selector, boolean transacted, boolean persistent, int messageSize, boolean verbose,
                      boolean afterCommit, boolean beforeCommit, boolean afterSend, boolean beforeSend, boolean failOnce,
                      int txBatchSize, int noOfDestinations, int rate, boolean pubsub, boolean unique) throws Exception
    {
        super(brokerDetails, username, password, virtualpath, destinationName, selector, transacted, persistent, messageSize,
              verbose, afterCommit, beforeCommit, afterSend, beforeSend, failOnce, txBatchSize, noOfDestinations, rate,
              pubsub, unique);

        _pingClientCount++;
    }

    /**
     * Returns the ping destinations themselves as the reply destinations for this pinger to listen to. This has the
     * effect of making this pinger listen to its own pings.
     *
     * @return The ping destinations.
     */
    public List<Destination> getReplyDestinations()
    {
        return _pingDestinations;
    }

    public int getConsumersPerTopic()
    {
        if (_isUnique)
        {
            return 1;
        }
        else
        {
            return _pingClientCount;
        }
    }

}
