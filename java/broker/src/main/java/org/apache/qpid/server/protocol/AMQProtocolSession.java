/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.protocol;

import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.AMQException;

import javax.security.sasl.SaslServer;


public interface AMQProtocolSession
{
    /**
     * Called when a protocol data block is received
     * @param message the data block that has been received
     * @throws Exception if processing the datablock fails
     */
    void dataBlockReceived(AMQDataBlock message) throws Exception;

    /**
     * Write a datablock, encoding where necessary (e.g. into a sequence of bytes)
     * @param frame the frame to be encoded and written
     */
    void writeFrame(AMQDataBlock frame);

    /**
     * Get the context key associated with this session. Context key is described
     * in the AMQ protocol specification (RFC 6).
     * @return the context key
     */
    String getContextKey();

    /**
     * Set the context key associated with this session. Context key is described
     * in the AMQ protocol specification (RFC 6).
     * @param contextKey the context key
     */
    void setContextKey(String contextKey);

    /**
     * Get the channel for this session associated with the specified id. A channel
     * id is unique per connection (i.e. per session).
     * @param channelId the channel id which must be valid
     * @return null if no channel exists, the channel otherwise
     */
    AMQChannel getChannel(int channelId) throws AMQException;

    /**
     * Associate a channel with this session.
     * @param channel the channel to associate with this session. It is an error to
     * associate the same channel with more than one session but this is not validated.
     */
    void addChannel(AMQChannel channel);

    /**
     * Close a specific channel. This will remove any resources used by the channel, including:
     * <ul><li>any queue subscriptions (this may in turn remove queues if they are auto delete</li>
     * </ul>
     * @param channelId id of the channel to close
     * @throws org.apache.qpid.AMQException if an error occurs closing the channel
     * @throws IllegalArgumentException if the channel id is not valid
     */
    void closeChannel(int channelId) throws AMQException;

    /**
     * Remove a channel from the session but do not close it.
     * @param channelId
     */
    void removeChannel(int channelId);

    /**
     * Initialise heartbeats on the session.
     * @param delay delay in seconds (not ms)
     */
    void initHeartbeats(int delay);

    /**
     * This must be called when the session is _closed in order to free up any resources
     * managed by the session.
     */
    void closeSession() throws AMQException;

    /**
     * @return a key that uniquely identifies this session
     */
    Object getKey();

    /**
     * Get the fully qualified domain name of the local address to which this session is bound. Since some servers
     * may be bound to multiple addresses this could vary depending on the acceptor this session was created from.
     *
     * @return a String FQDN
     */
    String getLocalFQDN();

    /**
     * @return the sasl server that can perform authentication for this session.
     */
    SaslServer getSaslServer();

    /**
     * Set the sasl server that is to perform authentication for this session.
     * @param saslServer
     */
    void setSaslServer(SaslServer saslServer);
}
