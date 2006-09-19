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
package org.apache.qpid.server.cluster.replay;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.ExchangeDeleteBody;
import org.apache.qpid.framing.QueueBindBody;
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.framing.ClusterSynchBody;
import org.apache.qpid.framing.BasicConsumeBody;
import org.apache.qpid.framing.BasicCancelBody;
import org.apache.qpid.server.cluster.ClusteredProtocolSession;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.server.cluster.util.Bindings;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores method invocations for replay to new members.
 *
 */
public class ReplayStore implements ReplayManager, StateAwareMethodListener
{
    private static final Logger _logger = Logger.getLogger(ReplayStore.class);

    private final Map<Class<? extends AMQMethodBody>, MethodRecorder> _globalRecorders = new HashMap<Class<? extends AMQMethodBody>, MethodRecorder>();
    private final Map<Class<? extends AMQMethodBody>, MethodRecorder> _localRecorders = new HashMap<Class<? extends AMQMethodBody>, MethodRecorder>();
    private final Map<String, QueueDeclareBody> _sharedQueues = new ConcurrentHashMap<String, QueueDeclareBody>();
    private final Map<String, QueueDeclareBody> _privateQueues = new ConcurrentHashMap<String, QueueDeclareBody>();
    private final Bindings<String, String, QueueBindBody> _sharedBindings = new Bindings<String, String, QueueBindBody>();
    private final Bindings<String, String, QueueBindBody> _privateBindings = new Bindings<String, String, QueueBindBody>();
    private final Map<String, ExchangeDeclareBody> _exchanges = new ConcurrentHashMap<String, ExchangeDeclareBody>();
    private final ConsumerCounts _consumers = new ConsumerCounts();

    public ReplayStore()
    {
        _globalRecorders.put(QueueDeclareBody.class, new SharedQueueDeclareRecorder());
        _globalRecorders.put(QueueDeleteBody.class, new SharedQueueDeleteRecorder());
        _globalRecorders.put(QueueBindBody.class, new SharedQueueBindRecorder());
        _globalRecorders.put(ExchangeDeclareBody.class, new ExchangeDeclareRecorder());
        _globalRecorders.put(ExchangeDeleteBody.class, new ExchangeDeleteRecorder());

        _localRecorders.put(QueueDeclareBody.class, new PrivateQueueDeclareRecorder());
        _localRecorders.put(QueueDeleteBody.class, new PrivateQueueDeleteRecorder());
        _localRecorders.put(QueueBindBody.class, new PrivateQueueBindRecorder());
        _localRecorders.put(BasicConsumeBody.class, new BasicConsumeRecorder());
        _localRecorders.put(BasicCancelBody.class, new BasicCancelRecorder());
        _localRecorders.put(ExchangeDeclareBody.class, new ExchangeDeclareRecorder());
        _localRecorders.put(ExchangeDeleteBody.class, new ExchangeDeleteRecorder());
    }

    public void methodReceived(AMQStateManager stateMgr, QueueRegistry queues, ExchangeRegistry exchanges, AMQProtocolSession session, AMQMethodEvent evt) throws AMQException
    {
        _logger.debug(new LogMessage("Replay store received {0}", evt.getMethod()));
        AMQMethodBody request = evt.getMethod();

        //allow any (relevant) recorder registered for this type of request to record it:
        MethodRecorder recorder = getRecorders(session).get(request.getClass());
        if (recorder != null)
        {
            recorder.record(request);
        }
    }

    private Map<Class<? extends AMQMethodBody>, MethodRecorder> getRecorders(AMQProtocolSession session)
    {
        if (ClusteredProtocolSession.isPeerSession(session))
        {
            return _globalRecorders;
        }
        else
        {
            return _localRecorders;
        }
    }

    public List<AMQMethodBody> replay(boolean isLeader)
    {
        List<AMQMethodBody> methods = new ArrayList<AMQMethodBody>();
        methods.addAll(_exchanges.values());
        methods.addAll(_privateQueues.values());
        synchronized(_privateBindings)
        {
            methods.addAll(_privateBindings.values());
        }
        if (isLeader)
        {
            methods.addAll(_sharedQueues.values());
            synchronized(_sharedBindings)
            {
                methods.addAll(_sharedBindings.values());
            }
        }
        _consumers.replay(methods);
        methods.add(new ClusterSynchBody());
        return methods;
    }

    private class BasicConsumeRecorder implements MethodRecorder<BasicConsumeBody>
    {
        public void record(BasicConsumeBody method)
        {
            if(_sharedQueues.containsKey(method.queue))
            {
                _consumers.increment(method.queue);
            }
        }
    }

    private class BasicCancelRecorder implements MethodRecorder<BasicCancelBody>
    {
        public void record(BasicCancelBody method)
        {
            if(_sharedQueues.containsKey(method.consumerTag))
            {
                _consumers.decrement(method.consumerTag);
            }
        }
    }

    private class SharedQueueDeclareRecorder extends QueueDeclareRecorder
    {
        SharedQueueDeclareRecorder()
        {
            super(false, _sharedQueues);
        }
    }

    private class PrivateQueueDeclareRecorder extends QueueDeclareRecorder
    {
        PrivateQueueDeclareRecorder()
        {
            super(true, _privateQueues, new SharedQueueDeclareRecorder());
        }
    }

    private class SharedQueueDeleteRecorder extends QueueDeleteRecorder
    {
        SharedQueueDeleteRecorder()
        {
            super(_sharedQueues, _sharedBindings);
        }
    }

    private class PrivateQueueDeleteRecorder extends QueueDeleteRecorder
    {
        PrivateQueueDeleteRecorder()
        {
            super(_privateQueues, _privateBindings, new SharedQueueDeleteRecorder());
        }
    }

    private class SharedQueueBindRecorder extends QueueBindRecorder
    {
        SharedQueueBindRecorder()
        {
            super(_sharedQueues, _sharedBindings);
        }
    }

    private class PrivateQueueBindRecorder extends QueueBindRecorder
    {
        PrivateQueueBindRecorder()
        {
            super(_privateQueues, _privateBindings, new SharedQueueBindRecorder());
        }
    }


    private static class QueueDeclareRecorder extends ChainedMethodRecorder<QueueDeclareBody>
    {
        private final boolean _exclusive;
        private final Map<String, QueueDeclareBody> _queues;

        QueueDeclareRecorder(boolean exclusive, Map<String, QueueDeclareBody> queues)
        {
            _queues = queues;
            _exclusive = exclusive;
        }

        QueueDeclareRecorder(boolean exclusive, Map<String, QueueDeclareBody> queues, QueueDeclareRecorder recorder)
        {
            super(recorder);
            _queues = queues;
            _exclusive = exclusive;
        }


        protected boolean doRecord(QueueDeclareBody method)
        {
            if (_exclusive == method.exclusive)
            {
                _queues.put(method.queue, method);
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    private class QueueDeleteRecorder extends ChainedMethodRecorder<QueueDeleteBody>
    {
        private final Map<String, QueueDeclareBody> _queues;
        private final Bindings<String, String, QueueBindBody> _bindings;

        QueueDeleteRecorder(Map<String, QueueDeclareBody> queues, Bindings<String, String, QueueBindBody> bindings)
        {
            this(queues, bindings, null);
        }

        QueueDeleteRecorder(Map<String, QueueDeclareBody> queues, Bindings<String, String, QueueBindBody> bindings, QueueDeleteRecorder recorder)
        {
            super(recorder);
            _queues = queues;
            _bindings = bindings;
        }

        protected boolean doRecord(QueueDeleteBody method)
        {
            if (_queues.remove(method.queue) != null)
            {
                _bindings.unbind1(method.queue);
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    private class QueueBindRecorder extends ChainedMethodRecorder<QueueBindBody>
    {
        private final Map<String, QueueDeclareBody> _queues;
        private final Bindings<String, String, QueueBindBody> _bindings;

        QueueBindRecorder(Map<String, QueueDeclareBody> queues, Bindings<String, String, QueueBindBody> bindings)
        {
            _queues = queues;
            _bindings = bindings;
        }

        QueueBindRecorder(Map<String, QueueDeclareBody> queues, Bindings<String, String, QueueBindBody> bindings, QueueBindRecorder recorder)
        {
            super(recorder);
            _queues = queues;
            _bindings = bindings;
        }

        protected boolean doRecord(QueueBindBody method)
        {
            if (_queues.containsKey(method.queue))
            {
                _bindings.bind(method.queue, method.exchange, method);
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    private class ExchangeDeclareRecorder implements MethodRecorder<ExchangeDeclareBody>
    {
        public void record(ExchangeDeclareBody method)
        {
            _exchanges.put(method.exchange, method);
        }
    }

    private class ExchangeDeleteRecorder implements MethodRecorder<ExchangeDeleteBody>
    {
        public void record(ExchangeDeleteBody method)
        {
            _exchanges.remove(method.exchange);
        }
    }
}
