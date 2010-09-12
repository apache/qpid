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

package org.apache.qpid.qmf;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.ConfigStore;
import org.apache.qpid.server.configuration.ConfiguredObject;
import org.apache.qpid.server.configuration.ExchangeConfigType;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeReferrer;
import org.apache.qpid.server.exchange.ExchangeType;
import org.apache.qpid.server.exchange.topic.TopicExchangeResult;
import org.apache.qpid.server.exchange.topic.TopicMatcherResult;
import org.apache.qpid.server.exchange.topic.TopicNormalizer;
import org.apache.qpid.server.exchange.topic.TopicParser;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.virtualhost.HouseKeepingTask;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

public class ManagementExchange implements Exchange, QMFService.Listener
{
    private static final AMQShortString QPID_MANAGEMENT = new AMQShortString("qpid.management");
    private static final AMQShortString QPID_MANAGEMENT_TYPE = new AMQShortString("management");

    private VirtualHost _virtualHost;

    private final TopicParser _parser = new TopicParser();

    private final Map<AMQShortString, TopicExchangeResult> _topicExchangeResults =
            new ConcurrentHashMap<AMQShortString, TopicExchangeResult>();

    private final Set<Binding> _bindingSet = new CopyOnWriteArraySet<Binding>();
    private UUID _id;
    private static final String AGENT_BANK = "0";

    private int _bindingCountHigh;
    private final AtomicLong _msgReceived = new AtomicLong();
    private final AtomicLong _bytesReceived = new AtomicLong();

    private final CopyOnWriteArrayList<BindingListener> _listeners = new CopyOnWriteArrayList<Exchange.BindingListener>();

    //TODO : persist creation time
    private long _createTime = System.currentTimeMillis();


    private class ManagementQueue implements BaseQueue
    {
        private final String NAME_AS_STRING = "##__mgmt_pseudo_queue__##" + UUID.randomUUID().toString();
        private final AMQShortString NAME_AS_SHORT_STRING = new AMQShortString(NAME_AS_STRING);

        public void enqueue(ServerMessage message) throws AMQException
        {
            long size = message.getSize();

            ByteBuffer buf = ByteBuffer.allocate((int) size);

            int offset = 0;

            while(offset < size)
            {
                offset += message.getContent(buf,offset);
            }

            buf.flip();
            QMFCommandDecoder commandDecoder = new QMFCommandDecoder(getQMFService(),buf);
            QMFCommand cmd;
            while((cmd = commandDecoder.decode()) != null)
            {
                cmd.process(_virtualHost, message);
            }

        }

        public void enqueue(ServerMessage message, PostEnqueueAction action) throws AMQException
        {
            enqueue(message);
        }

        public boolean isDurable()
        {
            return false;
        }

        public AMQShortString getNameShortString()
        {
            return NAME_AS_SHORT_STRING;
        }

        public String getResourceName()
        {
            return NAME_AS_STRING;
        }
    }


    private final ManagementQueue _mgmtQueue = new ManagementQueue();

    public ManagementExchange()
    {
    }

    public static final ExchangeType<ManagementExchange> TYPE = new ExchangeType<ManagementExchange>()
    {

        public AMQShortString getName()
        {
            return QPID_MANAGEMENT_TYPE;
        }

        public Class<ManagementExchange> getExchangeClass()
        {
            return ManagementExchange.class;
        }

        public ManagementExchange newInstance(VirtualHost host,
                                            AMQShortString name,
                                            boolean durable,
                                            int ticket,
                                            boolean autoDelete) throws AMQException
        {
            ManagementExchange exch = new ManagementExchange();
            exch.initialise(host, name, durable, ticket, autoDelete);
            return exch;
        }

        public AMQShortString getDefaultExchangeName()
        {
            return QPID_MANAGEMENT;
        }
    };


    public AMQShortString getNameShortString()
    {
        return QPID_MANAGEMENT;
    }

    public AMQShortString getTypeShortString()
    {
        return QPID_MANAGEMENT_TYPE;
    }

    public void initialise(VirtualHost host, AMQShortString name, boolean durable, int ticket, boolean autoDelete)
            throws AMQException
    {
        if(!QPID_MANAGEMENT.equals(name))
        {
            throw new AMQException("Can't create more than one Management exchange");
        }
        _virtualHost = host;
        _id = host.getConfigStore().createId();
        _virtualHost.scheduleHouseKeepingTask(_virtualHost.getBroker().getManagementPublishInterval(), new UpdateTask(_virtualHost));
        getConfigStore().addConfiguredObject(this);
        getQMFService().addListener(this);
    }

    public UUID getId()
    {
        return _id;
    }

    public ExchangeConfigType getConfigType()
    {
        return ExchangeConfigType.getInstance();
    }

    public ConfiguredObject getParent()
    {
        return _virtualHost;
    }

    public boolean isDurable()
    {
        return true;
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public String getName()
    {
        return QPID_MANAGEMENT.toString();
    }

    public ExchangeType getType()
    {
        return TYPE;
    }

    public boolean isAutoDelete()
    {
        return false;
    }

    public int getTicket()
    {
        return 0;
    }

    public void close() throws AMQException
    {
        getConfigStore().removeConfiguredObject(this);
    }

    public ConfigStore getConfigStore()
    {
        return getVirtualHost().getConfigStore();
    }

    public synchronized void addBinding(final Binding b)
    {

        if(_bindingSet.add(b))
        {
            AMQShortString routingKey = TopicNormalizer.normalize(new AMQShortString(b.getBindingKey()));

            TopicExchangeResult result = _topicExchangeResults.get(routingKey);
            if(result == null)
            {
                result = new TopicExchangeResult();
                result.addUnfilteredQueue(b.getQueue());
                _parser.addBinding(routingKey, result);
                _topicExchangeResults.put(routingKey,result);
            }
            else
            {
                result.addUnfilteredQueue(b.getQueue());
            }

            result.addBinding(b);
        }
        
        for(BindingListener listener : _listeners)
        {
            listener.bindingAdded(this, b);
        }

        if(_bindingSet.size() > _bindingCountHigh)
        {
            _bindingCountHigh = _bindingSet.size();
        }
        
        String bindingKey = b.getBindingKey();

        if(bindingKey.startsWith("schema.") || bindingKey.startsWith("*.") || bindingKey.startsWith("#."))
        {
            publishAllSchema();
        }
        if(bindingKey.startsWith("console.") || bindingKey.startsWith("*.") || bindingKey.startsWith("#."))
        {
            publishAllConsole();
        }

    }

    void publishAllConsole()
    {
        QMFService qmfService = getQMFService();

        long sampleTime = System.currentTimeMillis();

        for(QMFPackage pkg : qmfService.getSupportedSchemas())
        {
            for(QMFClass qmfClass : pkg.getClasses())
            {
                Collection<QMFObject> qmfObjects = qmfService.getObjects(qmfClass);

                publishObjectsToConsole(sampleTime, qmfObjects);
            }

        }

    }

    private QMFService getQMFService()
    {
        return _virtualHost.getApplicationRegistry().getQMFService();
    }

    void publishObjectsToConsole(final long sampleTime,
                                 final Collection<QMFObject> qmfObjects)
    {
        if(!qmfObjects.isEmpty() && hasBindings())
        {
            QMFClass qmfClass = qmfObjects.iterator().next().getQMFClass();
            ArrayList<QMFCommand> commands = new ArrayList<QMFCommand>();


            for(QMFObject obj : qmfObjects)
            {
                commands.add(obj.asConfigInfoCmd(sampleTime));
                commands.add(obj.asInstrumentInfoCmd(sampleTime));
            }

            publishToConsole(qmfClass, commands);
        }
    }

    private void publishToConsole(final QMFClass qmfClass, final ArrayList<QMFCommand> commands)
    {
        if(!commands.isEmpty() && hasBindings())
        {
            String routingKey = "console.obj.1." + AGENT_BANK + "." + qmfClass.getPackage().getName() + "." + qmfClass.getName();
            QMFMessage message = new QMFMessage(routingKey,commands.toArray(new QMFCommand[commands.size()]));

            Collection<TopicMatcherResult> results = _parser.parse(new AMQShortString(routingKey));
            HashSet<AMQQueue> queues = new HashSet<AMQQueue>();
            for(TopicMatcherResult result : results)
            {
                TopicExchangeResult res = (TopicExchangeResult)result;

                for(Binding b : res.getBindings())
                {
                    b.incrementMatches();
                }
                
                queues.addAll(((TopicExchangeResult)result).getUnfilteredQueues());
            }
            for(AMQQueue queue : queues)
            {
                try
                {
                    queue.enqueue(message);
                }
                catch (AMQException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    void publishAllSchema()
    {

    }

    public synchronized void removeBinding(final Binding binding)
    {
        if(_bindingSet.remove(binding))
        {
            AMQShortString bindingKey = TopicNormalizer.normalize(new AMQShortString(binding.getBindingKey()));
            TopicExchangeResult result = _topicExchangeResults.get(bindingKey);
            result.removeBinding(binding);
            result.removeUnfilteredQueue(binding.getQueue());
        }

        for(BindingListener listener : _listeners)
        {
            listener.bindingRemoved(this, binding);
        }
    }

    public synchronized Collection<Binding> getBindings()
    {
        return new ArrayList<Binding>(_bindingSet);
    }

    public ArrayList<BaseQueue> route(InboundMessage message)
    {
        ArrayList<BaseQueue> queues = new ArrayList<BaseQueue>(1);
        _msgReceived.incrementAndGet();
        _bytesReceived.addAndGet(message.getSize());
        queues.add(_mgmtQueue);
        return queues;
    }

    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isBound(AMQShortString routingKey)
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isBound(AMQQueue queue)
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean hasBindings()
    {
        return !_bindingSet.isEmpty();
    }

    public boolean isBound(String bindingKey, AMQQueue queue)
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isBound(String bindingKey)
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void addCloseTask(final Task task)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void removeCloseTask(final Task task)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }



    public Exchange getAlternateExchange()
    {
        return null;
    }

    public Map<String, Object> getArguments()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setAlternateExchange(Exchange exchange)
    {

    }

    public void removeReference(ExchangeReferrer exchange)
    {
    }

    public void addReference(ExchangeReferrer exchange)
    {
    }

    public boolean hasReferrers()
    {
        return true;
    }



    private class UpdateTask extends HouseKeepingTask
    {
        public UpdateTask(VirtualHost vhost)
        {
            super(vhost);
        }

        public void execute()
        {
            publishAllConsole();
            publishAllSchema();
        }

    }

    public void objectCreated(final QMFObject obj)
    {
        publishObjectsToConsole(System.currentTimeMillis(), Collections.singleton(obj));
    }

    public void objectDeleted(final QMFObject obj)
    {
        publishObjectsToConsole(System.currentTimeMillis(), Collections.singleton(obj));
    }

    public long getBindingCount()
    {
        return getBindings().size();
    }

    public long getBindingCountHigh()
    {
        return _bindingCountHigh;
    }

    public long getMsgReceives()
    {
        return _msgReceived.get();
    }

    public long getMsgRoutes()
    {
        return getMsgReceives();
    }

    public long getByteReceives()
    {
        return _bytesReceived.get();
    }

    public long getByteRoutes()
    {
        return getByteReceives();
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    public void addBindingListener(final BindingListener listener)
    {
        _listeners.add(listener);
    }

    public void removeBindingListener(final BindingListener listener)
    {
        _listeners.remove(listener);
    }

}
