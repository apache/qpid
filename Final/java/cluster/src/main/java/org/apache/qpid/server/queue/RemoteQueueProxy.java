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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.cluster.ClusteredProtocolSession;
import org.apache.qpid.server.cluster.GroupManager;
import org.apache.qpid.server.cluster.MemberHandle;
import org.apache.qpid.server.cluster.SimpleSendable;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.server.virtualhost.VirtualHost;

/**
 * TODO: separate out an abstract base class from AMQQueue from which this inherits. It does
 * not require all the functionality currently in AMQQueue.
 *
 */
public class RemoteQueueProxy extends AMQQueue
{
    private static final Logger _logger = Logger.getLogger(RemoteQueueProxy.class);
    private final MemberHandle _target;
    private final GroupManager _groupMgr;

    public RemoteQueueProxy(MemberHandle target, GroupManager groupMgr, AMQShortString name, boolean durable, AMQShortString owner, boolean autoDelete, VirtualHost virtualHost)
            throws AMQException
    {
        super(name, durable, owner, autoDelete, virtualHost);
        _target = target;
        _groupMgr = groupMgr;
        _groupMgr.addMemberhipChangeListener(new ProxiedQueueCleanup(target, this));
    }


    public void deliver(AMQMessage msg) throws NoConsumersException
    {
        if (ClusteredProtocolSession.canRelay(msg, _target))
        {
            try
            {
                _logger.debug(new LogMessage("Relaying {0} to {1}", msg, _target));
                relay(msg);
            }
            catch (NoConsumersException e)
            {
                throw e;
            }
            catch (AMQException e)
            {
                //TODO: sort out exception handling...
                e.printStackTrace();
            }
        }
        else
        {
            _logger.debug(new LogMessage("Cannot relay {0} to {1}", msg, _target));
        }
    }

    void relay(AMQMessage msg) throws AMQException
    {
        // TODO FIXME - can no longer update the publish body as it is an opaque wrapper object
        // if cluster can handle immediate then it should wrap the wrapper...
        
//        BasicPublishBody publish = msg.getMessagePublishInfo();
//        publish.immediate = false; //can't as yet handle the immediate flag in a cluster

        // send this on to the broker for which it is acting as proxy:
        _groupMgr.send(_target, new SimpleSendable(msg));
    }
}
