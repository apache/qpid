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
package org.apache.qpid.server.protocol.v0_8;

import java.net.SocketAddress;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.locks.Lock;

import javax.security.auth.Subject;
import javax.security.sasl.SaslServer;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.MethodDispatcher;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.v0_8.output.ProtocolOutputConverter;
import org.apache.qpid.server.security.AuthorizationHolder;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;


public interface AMQProtocolSession<T extends AMQProtocolSession<T>>
        extends AMQVersionAwareProtocolSession, AuthorizationHolder, AMQConnectionModel<T,AMQChannel<T>>
{
    long getSessionID();

    void setMaxFrameSize(int frameMax);

    long getMaxFrameSize();

    boolean isClosing();

    void flushBatched();

    void setDeferFlush(boolean defer);

    ClientDeliveryMethod createDeliveryMethod(int channelId);

    long getLastReceivedTime();

    /**
     * Return the local socket address for the connection
     *
     * @return the socket address
     */
    SocketAddress getLocalAddress();

    /**
     * Get the context key associated with this session. Context key is described in the AMQ protocol specification (RFC
     * 6).
     *
     * @return the context key
     */
    AMQShortString getContextKey();

    /**
     * Set the context key associated with this session. Context key is described in the AMQ protocol specification (RFC
     * 6).
     *
     * @param contextKey the context key
     */
    void setContextKey(AMQShortString contextKey);

    /**
     * Get the channel for this session associated with the specified id. A channel id is unique per connection (i.e.
     * per session).
     *
     * @param channelId the channel id which must be valid
     *
     * @return null if no channel exists, the channel otherwise
     */
    AMQChannel<T> getChannel(int channelId);

    /**
     * Associate a channel with this session.
     *
     * @param channel the channel to associate with this session. It is an error to associate the same channel with more
     *                than one session but this is not validated.
     */
    void addChannel(AMQChannel<T> channel) throws AMQException;

    /**
     * Close a specific channel. This will remove any resources used by the channel, including: <ul><li>any queue
     * subscriptions (this may in turn remove queues if they are auto delete</li> </ul>
     *
     * @param channelId id of the channel to close
     *
     * @throws org.apache.qpid.AMQException if an error occurs closing the channel
     * @throws IllegalArgumentException     if the channel id is not valid
     */
    void closeChannel(int channelId) throws AMQException;

    void closeChannel(int channelId, AMQConstant cause, String message) throws AMQException;

    /**
     * Marks the specific channel as closed. This will release the lock for that channel id so a new channel can be
     * created on that id.
     *
     * @param channelId id of the channel to close
     */
    void closeChannelOk(int channelId);

    /**
     * Check to see if this chanel is closing
     *
     * @param channelId id to check
     * @return boolean with state of channel awaiting closure
     */
    boolean channelAwaitingClosure(int channelId);

    /**
     * Remove a channel from the session but do not close it.
     *
     * @param channelId
     */
    void removeChannel(int channelId);

    /**
     * Initialise heartbeats on the session.
     *
     * @param delay delay in seconds (not ms)
     */
    void initHeartbeats(int delay);

    /** This must be called when the session is _closed in order to free up any resources managed by the session. */
    void closeSession();

    void closeProtocolSession();

    /** @return a key that uniquely identifies this session */
    Object getKey();

    /**
     * Get the fully qualified domain name of the local address to which this session is bound. Since some servers may
     * be bound to multiple addresses this could vary depending on the acceptor this session was created from.
     *
     * @return a String FQDN
     */
    String getLocalFQDN();

    /** @return the sasl server that can perform authentication for this session. */
    SaslServer getSaslServer();

    /**
     * Set the sasl server that is to perform authentication for this session.
     *
     * @param saslServer
     */
    void setSaslServer(SaslServer saslServer);

    void setClientProperties(FieldTable clientProperties);

    Object getReference();

    VirtualHostImpl<?,?,?> getVirtualHost();

    void setVirtualHost(VirtualHostImpl<?,?,?> virtualHost) throws AMQException;

    public ProtocolOutputConverter getProtocolOutputConverter();

    void setAuthorizedSubject(Subject authorizedSubject);

    public java.net.SocketAddress getRemoteAddress();

    public MethodRegistry getMethodRegistry();

    public MethodDispatcher getMethodDispatcher();

    String getClientVersion();

    long getLastIoTime();

    long getWrittenBytes();

    Long getMaximumNumberOfChannels();

    void setMaximumNumberOfChannels(Long value);

    List<AMQChannel<T>> getChannels();

    public Principal getPeerPrincipal();

    Lock getReceivedLock();

    /**
     * Used for 0-8/0-9/0-9-1 connections to choose to close
     * the connection when a transactional session receives a 'mandatory' message which
     * can't be routed rather than returning the message.
     */
    boolean isCloseWhenNoRoute();

    boolean isCompressionSupported();

    int getMessageCompressionThreshold();
}
