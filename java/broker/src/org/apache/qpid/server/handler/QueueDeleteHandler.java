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
package org.apache.qpid.server.handler;

import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.framing.QueueDeleteOkBody;
import org.apache.qpid.AMQException;

public class QueueDeleteHandler  implements StateAwareMethodListener<QueueDeleteBody>
{
    private static final QueueDeleteHandler _instance = new QueueDeleteHandler();

    public static QueueDeleteHandler getInstance()
    {
        return _instance;
    }

    private final boolean _failIfNotFound;
    private final MessageStore _store;

    public QueueDeleteHandler()
    {
        this(true);
    }

    public QueueDeleteHandler(boolean failIfNotFound)
    {
        _failIfNotFound = failIfNotFound;
        _store = ApplicationRegistry.getInstance().getMessageStore();

    }

    public void methodReceived(AMQStateManager stateMgr, QueueRegistry queues, ExchangeRegistry exchanges, AMQProtocolSession session, AMQMethodEvent<QueueDeleteBody> evt) throws AMQException
    {
        QueueDeleteBody body = evt.getMethod();
        AMQQueue queue;
        if(body.queue == null)
        {
            queue = session.getChannel(evt.getChannelId()).getDefaultQueue();
        }
        else
        {
            queue = queues.getQueue(body.queue);
        }

        if(queue == null)
        {
            if(_failIfNotFound)
            {
                throw body.getChannelException(404, "Queue " + body.queue + " does not exist.");
            }
        }
        else
        {
            int purged = queue.delete(body.ifUnused, body.ifEmpty);
            _store.removeQueue(queue.getName());
            session.writeFrame(QueueDeleteOkBody.createAMQFrame(evt.getChannelId(), purged));
        }
    }
}
