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
package org.apache.qpid.server.txn;

import java.nio.ByteBuffer;

import org.apache.commons.lang.NotImplementedException;
import org.apache.qpid.server.configuration.SessionConfig;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;

/**
 * Mock Server Message allowing its persistent flag to be controlled from test.
 */
class MockServerMessage implements ServerMessage
{
    /**
     * 
     */
    private final boolean persistent;

    /**
     * @param persistent
     */
    MockServerMessage(boolean persistent)
    {
        this.persistent = persistent;
    }

    @Override
    public boolean isPersistent()
    {
        return persistent;
    }

    @Override
    public MessageReference newReference()
    {
        throw new NotImplementedException();
    }

    @Override
    public boolean isImmediate()
    {
        throw new NotImplementedException();
    }

    @Override
    public long getSize()
    {
        throw new NotImplementedException();
    }

    @Override
    public SessionConfig getSessionConfig()
    {
        throw new NotImplementedException();
    }

    @Override
    public String getRoutingKey()
    {
        throw new NotImplementedException();
    }

    @Override
    public AMQMessageHeader getMessageHeader()
    {
        throw new NotImplementedException();
    }

    @Override
    public long getExpiration()
    {
        throw new NotImplementedException();
    }

    @Override
    public int getContent(ByteBuffer buf, int offset)
    {
        throw new NotImplementedException();
    }

    @Override
    public long getArrivalTime()
    {
        throw new NotImplementedException();
    }

    @Override
    public Long getMessageNumber()
    {
        return 0L;
    }
}