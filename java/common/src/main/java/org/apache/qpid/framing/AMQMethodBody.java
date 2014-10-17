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
package org.apache.qpid.framing;

import java.io.DataOutput;
import java.io.IOException;

import org.apache.qpid.AMQChannelException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;

public interface AMQMethodBody extends AMQBody
{
    public static final byte TYPE = 1;

    /** @return unsigned short */
    public int getClazz();

    /** @return unsigned short */
    public int getMethod();

    public void writeMethodPayload(DataOutput buffer) throws IOException;


    public int getSize();

    public void writePayload(DataOutput buffer) throws IOException;

    public AMQFrame generateFrame(int channelId);

    public String toString();



    /**
     * Convenience Method to create a channel not found exception
     *
     * @param channelId The channel id that is not found
     *
     * @param methodRegistry
     * @return new AMQChannelException
     */
    public AMQChannelException getChannelNotFoundException(int channelId, final MethodRegistry methodRegistry);

    public AMQChannelException getChannelException(AMQConstant code,
                                                   String message,
                                                   final MethodRegistry methodRegistry);

    public AMQConnectionException getConnectionException(AMQConstant code,
                                                         String message,
                                                         final MethodRegistry methodRegistry);


    public boolean execute(MethodDispatcher methodDispatcher, int channelId) throws AMQException;
}
