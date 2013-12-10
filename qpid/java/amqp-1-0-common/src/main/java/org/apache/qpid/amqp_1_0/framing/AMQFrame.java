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

package org.apache.qpid.amqp_1_0.framing;

import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.FrameBody;

import java.nio.ByteBuffer;

public abstract class AMQFrame<T>
{
    private T _frameBody;
    private ByteBuffer _payload;

    AMQFrame(T frameBody)
    {
        _frameBody = frameBody;
    }

    protected AMQFrame(T frameBody, ByteBuffer payload)
    {
        _frameBody = frameBody;
        _payload = payload;
    }

    public ByteBuffer getPayload()
    {
        return _payload;
    }

    public static TransportFrame createAMQFrame(short channel, FrameBody frameBody)
    {
        return createAMQFrame(channel, frameBody, null);
    }

    public static TransportFrame createAMQFrame(short channel, FrameBody frameBody, ByteBuffer payload)
    {
        return new TransportFrame(channel, frameBody, payload);
    }

    abstract public short getChannel();

    abstract public byte getFrameType();

    public T getFrameBody()
    {
        return _frameBody;
    }

    @Override
    public String toString()
    {
        return "AMQFrame{" +
               "frameBody=" + _frameBody +
               '}';
    }
}
