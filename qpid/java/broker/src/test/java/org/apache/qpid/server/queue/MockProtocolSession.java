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
package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.framing.*;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.transactionlog.TransactionLog;
import org.apache.qpid.server.output.ProtocolOutputConverter;
import org.apache.qpid.server.output.ProtocolOutputConverterRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.transport.Sender;

import javax.security.sasl.SaslServer;
import java.util.HashMap;
import java.util.Map;
import java.security.Principal;

/**
 * A protocol session that can be used for testing purposes.
 */
public class MockProtocolSession implements AMQProtocolSession
{

    private Map<Integer, AMQChannel> _channelMap = new HashMap<Integer, AMQChannel>();

    public MockProtocolSession(TransactionLog transactionLog)
    {
    }

    public void dataBlockReceived(AMQDataBlock message) throws Exception
    {
    }

    public void writeFrame(AMQDataBlock frame)
    {
    }

    public AMQShortString getContextKey()
    {
        return null;
    }

    public void setContextKey(AMQShortString contextKey)
    {
    }

    public AMQChannel getChannel(int channelId)
    {
        AMQChannel channel = _channelMap.get(channelId);
        if (channel == null)
        {
            throw new IllegalArgumentException("Invalid channel id: " + channelId);
        }
        else
        {
            return channel;
        }
    }

    public void addChannel(AMQChannel channel)
    {
        if (channel == null)
        {
            throw new IllegalArgumentException("Channel must not be null");
        }
        else
        {
            _channelMap.put(channel.getChannelId(), channel);
        }
    }

    public void closeChannel(int channelId) throws AMQException
    {
    }

    public void closeChannelOk(int channelId)
    {
        
    }

    public boolean channelAwaitingClosure(int channelId)
    {
        return false;
    }

    public void removeChannel(int channelId)
    {
        _channelMap.remove(channelId);
    }

    public void initHeartbeats(int delay)
    {
    }

    public void closeSession() throws AMQException
    {
    }

    public void closeConnection(int channelId, AMQConnectionException e, boolean closeIoSession) throws AMQException
    {        
    }

    public Object getKey()
    {
        return null;
    }

    public String getLocalFQDN()
    {
        return null;
    }

    public SaslServer getSaslServer()
    {
        return null;
    }

    public void setSaslServer(SaslServer saslServer)
    {
    }

    public FieldTable getClientProperties()
    {
        return null;
    }

    public void setClientProperties(FieldTable clientProperties)
    {
    }
	
	public Object getClientIdentifier()
	{
		return null;
	}

    public VirtualHost getVirtualHost()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setVirtualHost(VirtualHost virtualHost)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void addSessionCloseTask(Task task)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void removeSessionCloseTask(Task task)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public ProtocolOutputConverter getProtocolOutputConverter()
    {
        return ProtocolOutputConverterRegistry.getConverter(this);
    }

    public void setAuthorizedID(Principal authorizedID)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Principal getAuthorizedID()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public MethodRegistry getMethodRegistry()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void methodFrameReceived(int channelId, AMQMethodBody body)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void contentHeaderReceived(int channelId, ContentHeaderBody body)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void contentBodyReceived(int channelId, ContentBody body)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void heartbeatBodyReceived(int channelId, HeartbeatBody body)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public MethodDispatcher getMethodDispatcher()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public ProtocolSessionIdentifier getSessionIdentifier()
    {
        return null;
    }

    public byte getProtocolMajorVersion()
    {
        return getProtocolVersion().getMajorVersion();
    }

    public byte getProtocolMinorVersion()
    {
        return getProtocolVersion().getMinorVersion();
    }


    public ProtocolVersion getProtocolVersion()
    {
        return ProtocolVersion.getLatestSupportedVersion();  //To change body of implemented methods use File | Settings | File Templates.
    }


    public VersionSpecificRegistry getRegistry()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setSender(Sender<java.nio.ByteBuffer> sender)
    {
        // FIXME AS TODO
        
    }

    public void init()
    {
        // TODO Auto-generated method stub
        
    }
}
