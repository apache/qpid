package org.apache.qpid.client.protocol;
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


import java.util.UUID;

import javax.security.sasl.SaslClient;

import org.apache.commons.lang.StringUtils;
import org.apache.mina.common.IdleStatus;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.ConnectionTuneParameters;
import org.apache.qpid.client.handler.ClientMethodDispatcherImpl;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ProtocolInitiation;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.transport.Sender;

public class AMQIoTransportProtocolSession extends AMQProtocolSession
{

    protected Sender<java.nio.ByteBuffer> _ioSender;
    private SaslClient _saslClient;
    private ConnectionTuneParameters _connectionTuneParameters;
    
    public AMQIoTransportProtocolSession(AMQProtocolHandler protocolHandler, AMQConnection connection)
    {
        super(protocolHandler, connection);
    }
    
    @Override
    public void closeProtocolSession(boolean waitLast) throws AMQException
    {
        _ioSender.close();
        _protocolHandler.getStateManager().changeState(AMQState.CONNECTION_CLOSED);
    }

    @Override
    public void init()
    {
        _ioSender.send(new ProtocolInitiation(_connection.getProtocolVersion()).toNioByteBuffer());
        _ioSender.flush();
    }

    @Override
    protected AMQShortString generateQueueName()
    {
        int id;
        synchronized (_queueIdLock)
        {
            id = _queueId++;
        }
        return new AMQShortString("tmp_" + UUID.randomUUID() + "_" + id);
    }
    
    @Override
    public AMQConnection getAMQConnection()
    {
        return _connection;
    }
    
    @Override
    public SaslClient getSaslClient()
    {
        return _saslClient;
    }
    
    @Override
    public void setSaslClient(SaslClient client)
    {
        _saslClient = client;
    }
    
    /** @param delay delay in seconds (not ms) */
    @Override
    void initHeartbeats(int delay)
    {
        if (delay > 0)
        {
            // FIXME: actually do something here
            HeartbeatDiagnostics.init(delay, HeartbeatConfig.CONFIG.getTimeout(delay));
        }
    }
    
    @Override
    public void methodFrameReceived(final int channel, final AMQMethodBody amqMethodBody) throws AMQException
    {
        // FIXME?
        _protocolHandler.methodBodyReceived(channel, amqMethodBody, null);
    }
    
    @Override
    public void writeFrame(AMQDataBlock frame, boolean wait)
    {      
        _ioSender.send(frame.toNioByteBuffer());
        if (wait)
        {
            _ioSender.flush();
        }
    }
    
    @Override
    public void setSender(Sender<java.nio.ByteBuffer> sender)
    {
        _ioSender = sender;
    }
 
    @Override
    public ConnectionTuneParameters getConnectionTuneParameters()
    {
        return _connectionTuneParameters;
    }
    
    @Override
    public void setConnectionTuneParameters(ConnectionTuneParameters params)
    {
        _connectionTuneParameters = params;
        AMQConnection con = getAMQConnection();
        con.setMaximumChannelCount(params.getChannelMax());
        con.setMaximumFrameSize(params.getFrameMax());
        initHeartbeats((int) params.getHeartbeat());
    }
}
