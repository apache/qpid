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
import org.apache.qpid.server.cluster.SimpleSendable;
import org.apache.qpid.server.cluster.GroupManager;
import org.apache.qpid.server.cluster.SimpleBodySendable;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.framing.AMQShortString;

import java.util.concurrent.Executor;

/**
 * Used to represent a private queue held locally.
 *
 */
public class PrivateQueue extends AMQQueue
{
    private final GroupManager _groupMgr;

    public PrivateQueue(GroupManager groupMgr, AMQShortString name, boolean durable, AMQShortString owner, boolean autoDelete, VirtualHost virtualHost)
            throws AMQException
    {
        super(name, durable, owner, autoDelete, virtualHost);
        _groupMgr = groupMgr;

    }

    protected void autodelete() throws AMQException
    {
        //delete locally:
        super.autodelete();

        //send delete request to peers:
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        QueueDeleteBody request = new QueueDeleteBody((byte)8, (byte)0,
                                                      QueueDeleteBody.getClazz((byte)8, (byte)0),
                                                      QueueDeleteBody.getMethod((byte)8, (byte)0),
                                                      false,false,false,null,0);
        request.queue = getName();
        _groupMgr.broadcast(new SimpleBodySendable(request));
    }
}
