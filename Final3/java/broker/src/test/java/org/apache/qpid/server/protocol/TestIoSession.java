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
package org.apache.qpid.server.protocol;

import org.apache.mina.common.*;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.qpid.pool.ReadWriteThreadModel;

import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test implementation of IoSession, which is required for some tests. Methods not being used are not implemented,
 * so if this class is being used and some methods are to be used, then please update those.
 */
public class TestIoSession implements IoSession
{
    private final ConcurrentMap attributes = new ConcurrentHashMap();

    public TestIoSession()
    {
    }

    public IoService getService()
    {
        return null;
    }

    public IoServiceConfig getServiceConfig()
    {
        return new TestIoConfig();
    }

    public IoHandler getHandler()
    {
        return null;
    }

    public IoSessionConfig getConfig()
    {
        return null;
    }

    public IoFilterChain getFilterChain()
    {
        return null;
    }

    public WriteFuture write(Object message)
    {
        return null;
    }

    public CloseFuture close()
    {
        return null;
    }

    public Object getAttachment()
    {
        return getAttribute("");
    }

    public Object setAttachment(Object attachment)
    {
        return setAttribute("",attachment);
    }

    public Object getAttribute(String key)
    {
        return attributes.get(key);
    }

    public Object setAttribute(String key, Object value)
    {
        return attributes.put(key,value);
    }

    public Object setAttribute(String key)
    {
        return attributes.put(key, Boolean.TRUE);
    }

    public Object removeAttribute(String key)
    {
        return attributes.remove(key);
    }

    public boolean containsAttribute(String key)
    {
        return attributes.containsKey(key);
    }

    public Set getAttributeKeys()
    {
        return attributes.keySet();
    }

    public TransportType getTransportType()
    {
        return null;
    }

    public boolean isConnected()
    {
        return false;
    }

    public boolean isClosing()
    {
        return false;
    }

    public CloseFuture getCloseFuture()
    {
        return null;
    }

    public SocketAddress getRemoteAddress()
    {
        return new InetSocketAddress("127.0.0.1", 1234);
    }

    public SocketAddress getLocalAddress()
    {
        return null;
    }

    public SocketAddress getServiceAddress()
    {
        return null;
    }

    public int getIdleTime(IdleStatus status)
    {
        return 0;
    }

    public long getIdleTimeInMillis(IdleStatus status)
    {
        return 0;
    }

    public void setIdleTime(IdleStatus status, int idleTime)
    {

    }

    public int getWriteTimeout()
    {
        return 0;
    }

    public long getWriteTimeoutInMillis()
    {
        return 0;
    }

    public void setWriteTimeout(int writeTimeout)
    {

    }

    public TrafficMask getTrafficMask()
    {
        return null;
    }

    public void setTrafficMask(TrafficMask trafficMask)
    {

    }

    public void suspendRead()
    {

    }

    public void suspendWrite()
    {

    }

    public void resumeRead()
    {

    }

    public void resumeWrite()
    {

    }

    public long getReadBytes()
    {
        return 0;
    }

    public long getWrittenBytes()
    {
        return 0;
    }

    public long getReadMessages()
    {
        return 0;
    }

    public long getWrittenMessages()
    {
        return 0;
    }

    public long getWrittenWriteRequests()
    {
        return 0;
    }

    public int getScheduledWriteRequests()
    {
        return 0;
    }

    public int getScheduledWriteBytes()
    {
        return 0;
    }

    public long getCreationTime()
    {
        return 0;
    }

    public long getLastIoTime()
    {
        return 0;
    }

    public long getLastReadTime()
    {
        return 0;
    }

    public long getLastWriteTime()
    {
        return 0;
    }

    public boolean isIdle(IdleStatus status)
    {
        return false;
    }

    public int getIdleCount(IdleStatus status)
    {
        return 0;
    }

    public long getLastIdleTime(IdleStatus status)
    {
        return 0; 
    }

    /**
     * Test implementation of IoServiceConfig
     */
    private class TestIoConfig extends SocketAcceptorConfig
    {
        public ThreadModel getThreadModel()
        {
            return ReadWriteThreadModel.getInstance();
        }
    }
}
