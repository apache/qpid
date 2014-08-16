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
package org.apache.qpid.server.model;

import java.util.Collection;
import java.util.EnumSet;

public enum Protocol
{
    AMQP_0_8(ProtocolType.AMQP),
    AMQP_0_9(ProtocolType.AMQP),
    AMQP_0_9_1(ProtocolType.AMQP),
    AMQP_0_10(ProtocolType.AMQP),
    AMQP_1_0(ProtocolType.AMQP),
    JMX_RMI(ProtocolType.JMX),
    HTTP(ProtocolType.HTTP),
    RMI(ProtocolType.RMI);

    private final ProtocolType _protocolType;

    private Protocol(ProtocolType type)
    {
        _protocolType =  type;
    }

    public ProtocolType getProtocolType()
    {
        return _protocolType;
    }

    public boolean isAMQP()
    {
        return _protocolType == ProtocolType.AMQP;
    }

    public static Protocol valueOfObject(Object protocolObject)
    {
        Protocol protocol;
        if (protocolObject instanceof Protocol)
        {
            protocol = (Protocol) protocolObject;
        }
        else
        {
            try
            {
                protocol = Protocol.valueOf(String.valueOf(protocolObject));
            }
            catch (Exception e)
            {
                throw new IllegalArgumentException("Can't convert '" + protocolObject
                        + "' to one of the supported protocols: " + EnumSet.allOf(Protocol.class), e);
            }
        }
        return protocol;
    }

    public static boolean hasAmqpProtocol(Collection<Protocol> protocols)
    {
        for (Protocol protocol : protocols)
        {
            if (protocol.isAMQP())
            {
                return true;
            }
        }
        return false;
    }

    public static enum ProtocolType
    {
        AMQP, HTTP, JMX, RMI
    }
}
