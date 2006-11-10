/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.queue;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.server.cluster.ClusteredProtocolSession;
import org.apache.qpid.server.cluster.GroupManager;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.server.cluster.MemberHandle;
import org.apache.qpid.server.cluster.SimpleSendable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

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

    public RemoteQueueProxy(MemberHandle target, GroupManager groupMgr, String name, boolean durable, String owner, boolean autoDelete, QueueRegistry queueRegistry)
            throws AMQException
    {
        super(name, durable, owner, autoDelete, queueRegistry);
        _target = target;
        _groupMgr = groupMgr;
        _groupMgr.addMemberhipChangeListener(new ProxiedQueueCleanup(target, this));
    }

    public RemoteQueueProxy(MemberHandle target, GroupManager groupMgr, String name, boolean durable, String owner, boolean autoDelete, QueueRegistry queueRegistry, Executor asyncDelivery)
            throws AMQException
    {
        super(name, durable, owner, autoDelete, queueRegistry, asyncDelivery);
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
        BasicPublishBody publish = msg.getPublishBody();
        ContentHeaderBody header = msg.getContentHeaderBody();
        List<ContentBody> bodies = msg.getContentBodies();

        //(i) construct a new publishing block:
        publish.immediate = false;//can't as yet handle the immediate flag in a cluster
        List<AMQBody> parts = new ArrayList<AMQBody>(2 + bodies.size());
        parts.add(publish);
        parts.add(header);
        parts.addAll(bodies);

        //(ii) send this on to the broker for which it is acting as proxy:
        _groupMgr.send(_target, new SimpleSendable(parts));
    }
}
