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

package org.apache.qpid.client.protocol;

import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQMethodFactory;
import org.apache.qpid.framing.CommonContentHeaderProperties;
import org.apache.qpid.framing.AMQMethodBodyImpl;
import org.apache.qpid.AMQTimeoutException;
import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQMethodEvent;

import org.apache.mina.common.ByteBuffer;

import java.util.Map;
import java.util.HashMap;


public interface ProtocolOutputHandler
{

    void sendCommand(int channelId, AMQMethodBody command);

    AMQMethodBody sendCommandReceiveResponse(int channelId, AMQMethodBody command) throws AMQException;
    AMQMethodBody sendCommandReceiveResponse(int channelId, AMQMethodBody command, long timeout) throws AMQException;
    <T extends AMQMethodBody> T sendCommandReceiveResponse(int channelId, AMQMethodBody command, Class<T> responseClass, long timeout) throws AMQException;
    <T extends AMQMethodBody> T sendCommandReceiveResponse(int channelId, AMQMethodBody command, Class<T> responseClass) throws AMQException;

    AMQMethodFactory getAMQMethodFactory();

    void publishMessage(int channelId, AMQShortString exchangeName, AMQShortString routingKey, boolean immediate, boolean mandatory, ByteBuffer payload, CommonContentHeaderProperties contentHeaderProperties, int ticket);

    <M extends AMQMethodBody> boolean methodReceived(AMQMethodEvent<M> evt) throws Exception;

    void error(Exception e);
}
