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
import org.apache.qpid.common.Closeable;
import org.apache.qpid.qmf.schema.BrokerSchema;
import org.apache.qpid.server.configuration.*;
import org.apache.qpid.server.registry.IApplicationRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class QMFService implements ConfigStore.ConfigEventListener, Closeable
{


    private IApplicationRegistry _applicationRegistry;
    private ConfigStore _configStore;


    private final Map<String, QMFPackage> _supportedSchemas = new HashMap<String, QMFPackage>();
    private static final Map<String, ConfigObjectType> _qmfClassMapping = new HashMap<String, ConfigObjectType>();

    static
    {
        _qmfClassMapping.put("system", SystemConfigType.getInstance());
        _qmfClassMapping.put("broker", BrokerConfigType.getInstance());
        _qmfClassMapping.put("vhost", VirtualHostConfigType.getInstance());
        _qmfClassMapping.put("exchange", ExchangeConfigType.getInstance());
        _qmfClassMapping.put("queue", QueueConfigType.getInstance());
        _qmfClassMapping.put("binding", BindingConfigType.getInstance());
        _qmfClassMapping.put("connection", ConnectionConfigType.getInstance());
        _qmfClassMapping.put("session", SessionConfigType.getInstance());
        _qmfClassMapping.put("subscription", SubscriptionConfigType.getInstance());
        _qmfClassMapping.put("link", LinkConfigType.getInstance());
        _qmfClassMapping.put("bridge", BridgeConfigType.getInstance());
    }

    private final Map<ConfigObjectType, ConfigObjectAdapter> _adapterMap =
            new HashMap<ConfigObjectType, ConfigObjectAdapter>();
    private final Map<ConfigObjectType,QMFClass> _classMap =
            new HashMap<ConfigObjectType,QMFClass>();


    private final ConcurrentHashMap<QMFClass,ConcurrentHashMap<ConfiguredObject, QMFObject>> _managedObjects =
            new ConcurrentHashMap<QMFClass,ConcurrentHashMap<ConfiguredObject, QMFObject>>();

    private final ConcurrentHashMap<QMFClass,ConcurrentHashMap<UUID, QMFObject>> _managedObjectsById =
            new ConcurrentHashMap<QMFClass,ConcurrentHashMap<UUID, QMFObject>>();

    private static final BrokerSchema PACKAGE = BrokerSchema.getPackage();

    public static interface Listener
    {
        public void objectCreated(QMFObject obj);
        public void objectDeleted(QMFObject obj);
    }

    private final CopyOnWriteArrayList<Listener> _listeners = new CopyOnWriteArrayList<Listener>();

    abstract class ConfigObjectAdapter<Q extends QMFObject<S,D>, S extends QMFObjectClass<Q,D>, D extends QMFObject.Delegate, T extends ConfigObjectType<T,C>, C extends ConfiguredObject<T,C>>
    {
        private final T _type;
        private final S _qmfClass;


        protected ConfigObjectAdapter(final T type, final S qmfClass)
        {
            _type = type;
            _qmfClass = qmfClass;
            _adapterMap.put(type,this);
            _classMap.put(type,qmfClass);
        }

        public T getType()
        {
            return _type;
        }

        public S getQMFClass()
        {
            return _qmfClass;
        }

        protected final Q newInstance(D delegate)
        {
            return _qmfClass.newInstance(delegate);
        }

        public abstract Q createQMFObject(C configObject);
    }

    private ConfigObjectAdapter<BrokerSchema.SystemObject,
                                       BrokerSchema.SystemClass,
                                       BrokerSchema.SystemDelegate,
                                       SystemConfigType,
                                       SystemConfig> _systemObjectAdapter =
            new   ConfigObjectAdapter<BrokerSchema.SystemObject,
                                      BrokerSchema.SystemClass,
                                      BrokerSchema.SystemDelegate,
                                      SystemConfigType,
                                      SystemConfig>(SystemConfigType.getInstance(),
                                                    PACKAGE.getQMFClassInstance(BrokerSchema.SystemClass.class))
            {


                public BrokerSchema.SystemObject createQMFObject(
                        final SystemConfig configObject)
                {
                    return newInstance(new SystemDelegate(configObject));
                }
            };

    private ConfigObjectAdapter<BrokerSchema.BrokerObject,
                                       BrokerSchema.BrokerClass,
                                       BrokerSchema.BrokerDelegate,
                                       BrokerConfigType,
                                       BrokerConfig> _brokerObjectAdapter =
            new ConfigObjectAdapter<BrokerSchema.BrokerObject,
                                       BrokerSchema.BrokerClass,
                                       BrokerSchema.BrokerDelegate,
                                       BrokerConfigType,
                                       BrokerConfig>(BrokerConfigType.getInstance(),
                                                     PACKAGE.getQMFClassInstance(BrokerSchema.BrokerClass.class))
            {

                public BrokerSchema.BrokerObject createQMFObject(
                        final BrokerConfig configObject)
                {
                    return newInstance(new BrokerDelegate(configObject));
                }
            };

    private ConfigObjectAdapter<BrokerSchema.VhostObject,
                                       BrokerSchema.VhostClass,
                                       BrokerSchema.VhostDelegate,
                                       VirtualHostConfigType,
                                       VirtualHostConfig> _vhostObjectAdapter =
            new   ConfigObjectAdapter<BrokerSchema.VhostObject,
                                      BrokerSchema.VhostClass,
                                      BrokerSchema.VhostDelegate,
                                      VirtualHostConfigType,
                                      VirtualHostConfig>(VirtualHostConfigType.getInstance(),
                                                         PACKAGE.getQMFClassInstance(BrokerSchema.VhostClass.class))
            {

                public BrokerSchema.VhostObject createQMFObject(
                        final VirtualHostConfig configObject)
                {
                    return newInstance(new VhostDelegate(configObject));
                }
            };


    private ConfigObjectAdapter<BrokerSchema.ExchangeObject,
                                       BrokerSchema.ExchangeClass,
                                       BrokerSchema.ExchangeDelegate,
                                       ExchangeConfigType,
                                       ExchangeConfig> _exchangeObjectAdapter =
            new   ConfigObjectAdapter<BrokerSchema.ExchangeObject,
                                      BrokerSchema.ExchangeClass,
                                      BrokerSchema.ExchangeDelegate,
                                      ExchangeConfigType,
                                      ExchangeConfig>(ExchangeConfigType.getInstance(),
                                                      PACKAGE.getQMFClassInstance(BrokerSchema.ExchangeClass.class))
            {

                public BrokerSchema.ExchangeObject createQMFObject(
                        final ExchangeConfig configObject)
                {
                    return newInstance(new ExchangeDelegate(configObject));
                }
            };


    private ConfigObjectAdapter<BrokerSchema.QueueObject,
                                       BrokerSchema.QueueClass,
                                       BrokerSchema.QueueDelegate,
                                       QueueConfigType,
                                       QueueConfig> _queueObjectAdapter =
            new   ConfigObjectAdapter<BrokerSchema.QueueObject,
                                      BrokerSchema.QueueClass,
                                      BrokerSchema.QueueDelegate,
                                      QueueConfigType,
                                      QueueConfig>(QueueConfigType.getInstance(),
                                                   PACKAGE.getQMFClassInstance(BrokerSchema.QueueClass.class))
            {

                public BrokerSchema.QueueObject createQMFObject(
                        final QueueConfig configObject)
                {
                    return newInstance(new QueueDelegate(configObject));
                }
            };


    private ConfigObjectAdapter<BrokerSchema.BindingObject,
                                       BrokerSchema.BindingClass,
                                       BrokerSchema.BindingDelegate,
                                       BindingConfigType,
                                       BindingConfig> _bindingObjectAdapter =
            new   ConfigObjectAdapter<BrokerSchema.BindingObject,
                                      BrokerSchema.BindingClass,
                                      BrokerSchema.BindingDelegate,
                                      BindingConfigType,
                                      BindingConfig>(BindingConfigType.getInstance(),
                                                     PACKAGE.getQMFClassInstance(BrokerSchema.BindingClass.class))
            {

                public BrokerSchema.BindingObject createQMFObject(
                        final BindingConfig configObject)
                {
                    return newInstance(new BindingDelegate(configObject));
                }
            };


    private ConfigObjectAdapter<BrokerSchema.ConnectionObject,
                                       BrokerSchema.ConnectionClass,
                                       BrokerSchema.ConnectionDelegate,
                                       ConnectionConfigType,
                                       ConnectionConfig> _connectionObjectAdapter =
            new   ConfigObjectAdapter<BrokerSchema.ConnectionObject,
                                      BrokerSchema.ConnectionClass,
                                      BrokerSchema.ConnectionDelegate,
                                      ConnectionConfigType,
                                      ConnectionConfig>(ConnectionConfigType.getInstance(),
                                                        PACKAGE.getQMFClassInstance(BrokerSchema.ConnectionClass.class))
            {

                public BrokerSchema.ConnectionObject createQMFObject(
                        final ConnectionConfig configObject)
                {
                    return newInstance(new ConnectionDelegate(configObject));
                }
            };


    private ConfigObjectAdapter<BrokerSchema.SessionObject,
                                       BrokerSchema.SessionClass,
                                       BrokerSchema.SessionDelegate,
                                       SessionConfigType,
                                       SessionConfig> _sessionObjectAdapter =
            new   ConfigObjectAdapter<BrokerSchema.SessionObject,
                                      BrokerSchema.SessionClass,
                                      BrokerSchema.SessionDelegate,
                                      SessionConfigType,
                                      SessionConfig>(SessionConfigType.getInstance(),
                                                     PACKAGE.getQMFClassInstance(BrokerSchema.SessionClass.class))
            {

                public BrokerSchema.SessionObject createQMFObject(
                        final SessionConfig configObject)
                {
                    return newInstance(new SessionDelegate(configObject));
                }
            };

    private ConfigObjectAdapter<BrokerSchema.SubscriptionObject,
                                       BrokerSchema.SubscriptionClass,
                                       BrokerSchema.SubscriptionDelegate,
                                       SubscriptionConfigType,
                                       SubscriptionConfig> _subscriptionObjectAdapter =
            new ConfigObjectAdapter<BrokerSchema.SubscriptionObject,
                                       BrokerSchema.SubscriptionClass,
                                       BrokerSchema.SubscriptionDelegate,
                                       SubscriptionConfigType,
                                       SubscriptionConfig>(SubscriptionConfigType.getInstance(),
                                                           PACKAGE.getQMFClassInstance(BrokerSchema.SubscriptionClass.class))
            {

                public BrokerSchema.SubscriptionObject createQMFObject(
                        final SubscriptionConfig configObject)
                {
                    return newInstance(new SubscriptionDelegate(configObject));
                }
            };


    private ConfigObjectAdapter<BrokerSchema.LinkObject,
                                       BrokerSchema.LinkClass,
                                       BrokerSchema.LinkDelegate,
                                       LinkConfigType,
                                       LinkConfig> _linkObjectAdapter =
            new ConfigObjectAdapter<BrokerSchema.LinkObject,
                                       BrokerSchema.LinkClass,
                                       BrokerSchema.LinkDelegate,
                                       LinkConfigType,
                                       LinkConfig>(LinkConfigType.getInstance(),
                                                           PACKAGE.getQMFClassInstance(BrokerSchema.LinkClass.class))
            {

                public BrokerSchema.LinkObject createQMFObject(
                        final LinkConfig configObject)
                {
                    return newInstance(new LinkDelegate(configObject));
                }
            };


    private ConfigObjectAdapter<BrokerSchema.BridgeObject,
                                       BrokerSchema.BridgeClass,
                                       BrokerSchema.BridgeDelegate,
                                       BridgeConfigType,
                                       BridgeConfig> _bridgeObjectAdapter =
            new ConfigObjectAdapter<BrokerSchema.BridgeObject,
                                       BrokerSchema.BridgeClass,
                                       BrokerSchema.BridgeDelegate,
                                       BridgeConfigType,
                                       BridgeConfig>(BridgeConfigType.getInstance(),
                                                           PACKAGE.getQMFClassInstance(BrokerSchema.BridgeClass.class))
            {

                public BrokerSchema.BridgeObject createQMFObject(
                        final BridgeConfig configObject)
                {
                    return newInstance(new BridgeDelegate(configObject));
                }
            };



    public QMFService(ConfigStore configStore, IApplicationRegistry applicationRegistry)
    {
        _configStore = configStore;
        _applicationRegistry = applicationRegistry;
        registerSchema(PACKAGE);

        for(ConfigObjectType v : _qmfClassMapping.values())
        {
            configStore.addConfigEventListener(v, this);
        }
        init();
    }

    public void close()
    {
        for(ConfigObjectType v : _qmfClassMapping.values())
        {
            _configStore.removeConfigEventListener(v, this);
        }
        _listeners.clear();
        
        _managedObjects.clear();
        _managedObjectsById.clear();
        _classMap.clear();
        _adapterMap.clear();
        _supportedSchemas.clear();
    }

    
    public void registerSchema(QMFPackage qmfPackage)
    {
        _supportedSchemas.put(qmfPackage.getName(), qmfPackage);
    }


    public Collection<QMFPackage> getSupportedSchemas()
    {
        return _supportedSchemas.values();
    }

    public QMFPackage getPackage(String aPackage)
    {
        return _supportedSchemas.get(aPackage);
    }

    public void onEvent(final ConfiguredObject object, final ConfigStore.Event evt)
    {

        switch (evt)
        {
            case CREATED:
                manageObject(object);
                break;

            case DELETED:
                unmanageObject(object);
                break;
        }
    }

    public QMFObject getObjectById(QMFClass qmfclass, UUID id)
    {
        ConcurrentHashMap<UUID, QMFObject> map = _managedObjectsById.get(qmfclass);
        if(map != null)
        {
            return map.get(id);
        }
        else
        {
            return null;
        }
    }

    private void unmanageObject(final ConfiguredObject object)
    {
        final QMFClass qmfClass = _classMap.get(object.getConfigType());
        
        if(qmfClass == null)
        {
            return;
        }

        ConcurrentHashMap<ConfiguredObject, QMFObject> classObjects = _managedObjects.get(qmfClass);
        if(classObjects != null)
        {
            QMFObject qmfObject = classObjects.remove(object);
            if(qmfObject != null)
            {
                _managedObjectsById.get(qmfClass).remove(object.getId());
                objectRemoved(qmfObject);
            }
        }
    }

    private void manageObject(final ConfiguredObject object)
    {
        ConfigObjectAdapter adapter = _adapterMap.get(object.getConfigType());
        QMFObject qmfObject = adapter.createQMFObject(object);
        final QMFClass qmfClass = qmfObject.getQMFClass();
        ConcurrentHashMap<ConfiguredObject, QMFObject> classObjects = _managedObjects.get(qmfClass);

        if(classObjects == null)
        {
            classObjects = new ConcurrentHashMap<ConfiguredObject, QMFObject>();
            if(_managedObjects.putIfAbsent(qmfClass, classObjects) != null)
            {
                classObjects = _managedObjects.get(qmfClass);
            }
        }

        ConcurrentHashMap<UUID, QMFObject> classObjectsById = _managedObjectsById.get(qmfClass);
        if(classObjectsById == null)
        {
            classObjectsById = new ConcurrentHashMap<UUID, QMFObject>();
            if(_managedObjectsById.putIfAbsent(qmfClass, classObjectsById) != null)
            {
                classObjectsById = _managedObjectsById.get(qmfClass);
            }
        }

        classObjectsById.put(object.getId(),qmfObject);

        if(classObjects.putIfAbsent(object, qmfObject) == null)
        {
            objectAdded(qmfObject);
        }
    }

    private void objectRemoved(final QMFObject qmfObject)
    {
        qmfObject.setDeleteTime();
        for(Listener l : _listeners)
        {
            l.objectDeleted(qmfObject);
        }
    }

    private void objectAdded(final QMFObject qmfObject)
    {
        for(Listener l : _listeners)
        {
            l.objectCreated(qmfObject);
        }
    }

    public void addListener(Listener l)
    {
        _listeners.add(l);
    }

    public void removeListener(Listener l)
    {
        _listeners.remove(l);
    }


    private void init()
    {
        for(QMFClass qmfClass : PACKAGE.getClasses())
        {
            ConfigObjectType configType = getConfigType(qmfClass);
            if(configType != null)
            {
                Collection<ConfiguredObject> objects = _configStore.getConfiguredObjects(configType);
                for(ConfiguredObject object : objects)
                {
                    manageObject(object);
                }
            }
        }
    }

    public Collection<QMFObject> getObjects(QMFClass qmfClass)
    {
        ConcurrentHashMap<ConfiguredObject, QMFObject> classObjects = _managedObjects.get(qmfClass);
        if(classObjects != null)
        {
            return classObjects.values();
        }
        else
        {
            return Collections.EMPTY_SET;
        }
    }

    private QMFObject adapt(final ConfiguredObject object)
    {
        if(object == null)
        {
            return null;
        }

        QMFClass qmfClass = _classMap.get(object.getConfigType());
        ConcurrentHashMap<ConfiguredObject, QMFObject> classObjects = _managedObjects.get(qmfClass);
        if(classObjects != null)
        {
            QMFObject qmfObject = classObjects.get(object);
            if(qmfObject != null)
            {
                return qmfObject;
            }
        }

        return _adapterMap.get(object.getConfigType()).createQMFObject(object);
    }

    private ConfigObjectType getConfigType(final QMFClass qmfClass)
    {
        return _qmfClassMapping.get(qmfClass.getName());
    }

    private static class SystemDelegate implements BrokerSchema.SystemDelegate
    {
        private final SystemConfig _obj;

        public SystemDelegate(final SystemConfig obj)
        {
            _obj = obj;
        }

        public UUID getSystemId()
        {
            return _obj.getId();
        }

        public String getOsName()
        {
            return _obj.getOperatingSystemName();
        }

        public String getNodeName()
        {
            return _obj.getNodeName();
        }

        public String getRelease()
        {
            return _obj.getOSRelease();
        }

        public String getVersion()
        {
            return _obj.getOSVersion();
        }

        public String getMachine()
        {
            return _obj.getOSArchitecture();
        }

        public UUID getId()
        {
            return _obj.getId();
        }

        public long getCreateTime()
        {
            return _obj.getCreateTime();
        }
    }

    private class BrokerDelegate implements BrokerSchema.BrokerDelegate
    {
        private final BrokerConfig _obj;

        public BrokerDelegate(final BrokerConfig obj)
        {
            _obj = obj;
        }

        public BrokerSchema.SystemObject getSystemRef()
        {
            return (BrokerSchema.SystemObject) adapt(_obj.getSystem());
        }

        public String getName()
        {
            return "amqp-broker";
        }

        public Integer getPort()
        {
            return _obj.getPort();
        }

        public Integer getWorkerThreads()
        {
            return _obj.getWorkerThreads();
        }

        public Integer getMaxConns()
        {
            return _obj.getMaxConnections();
        }

        public Integer getConnBacklog()
        {
            return _obj.getConnectionBacklogLimit();
        }

        public Long getStagingThreshold()
        {
            return _obj.getStagingThreshold();
        }

        public Integer getMgmtPubInterval()
        {
            return _obj.getManagementPublishInterval();
        }

        public String getVersion()
        {
            return _obj.getVersion();
        }

        public String getDataDir()
        {
            return _obj.getDataDirectory();
        }

        public Long getUptime()
        {
            return (System.currentTimeMillis() - _obj.getCreateTime()) * 1000000L;
        }

        public BrokerSchema.BrokerClass.EchoMethodResponseCommand echo(final BrokerSchema.BrokerClass.EchoMethodResponseCommandFactory factory,
                                                                       final Long sequence,
                                                                       final String body)
        {
            return factory.createResponseCommand(sequence, body);
        }

        public BrokerSchema.BrokerClass.ConnectMethodResponseCommand connect(final BrokerSchema.BrokerClass.ConnectMethodResponseCommandFactory factory,
                                                                             final String host,
                                                                             final Long port,
                                                                             final Boolean durable,
                                                                             final String authMechanism,
                                                                             final String username,
                                                                             final String password,
                                                                             final String transport)
        {
            _obj.createBrokerConnection(transport, host, port.intValue(), durable, authMechanism, username, password);

            return factory.createResponseCommand();
        }

        public BrokerSchema.BrokerClass.QueueMoveMessagesMethodResponseCommand queueMoveMessages(final BrokerSchema.BrokerClass.QueueMoveMessagesMethodResponseCommandFactory factory,
                                                                                                 final String srcQueue,
                                                                                                 final String destQueue,
                                                                                                 final Long qty)
        {
            // TODO
            return factory.createResponseCommand(CompletionCode.NOT_IMPLEMENTED);
        }

        public BrokerSchema.BrokerClass.GetLogLevelMethodResponseCommand getLogLevel(final BrokerSchema.BrokerClass.GetLogLevelMethodResponseCommandFactory factory)
        {
            // TODO: The Java broker has numerous loggers, so we can't really implement this method properly.
            return factory.createResponseCommand(CompletionCode.NOT_IMPLEMENTED);
        }

        public BrokerSchema.BrokerClass.SetLogLevelMethodResponseCommand setLogLevel(final BrokerSchema.BrokerClass.SetLogLevelMethodResponseCommandFactory factory, String level)
        {
            // TODO: The Java broker has numerous loggers, so we can't really implement this method properly.
            return factory.createResponseCommand(CompletionCode.NOT_IMPLEMENTED);
        }

        public BrokerSchema.BrokerClass.CreateMethodResponseCommand create(final BrokerSchema.BrokerClass.CreateMethodResponseCommandFactory factory,
                                                                           final String type,
                                                                           final String name,
                                                                           final Map properties,
                                                                           final java.lang.Boolean lenient)
        {
            //TODO:
            return factory.createResponseCommand(CompletionCode.NOT_IMPLEMENTED);
        }

        public BrokerSchema.BrokerClass.DeleteMethodResponseCommand delete(final BrokerSchema.BrokerClass.DeleteMethodResponseCommandFactory factory,
                                                                           final String type,
                                                                           final String name,
                                                                           final Map options)
        {
            //TODO:
            return factory.createResponseCommand(CompletionCode.NOT_IMPLEMENTED);
        }

        public UUID getId()
        {
            return _obj.getId();
        }

        public long getCreateTime()
        {
            return _obj.getCreateTime();
        }
    }

    private class VhostDelegate implements BrokerSchema.VhostDelegate
    {
        private final VirtualHostConfig _obj;

        public VhostDelegate(final VirtualHostConfig obj)
        {
            _obj = obj;
        }

        public BrokerSchema.BrokerObject getBrokerRef()
        {
            return (BrokerSchema.BrokerObject) adapt(_obj.getBroker());
        }

        public String getName()
        {
            return _obj.getName();
        }

        public String getFederationTag()
        {
            return _obj.getFederationTag();
        }

        public UUID getId()
        {
            return _obj.getId();
        }

        public long getCreateTime()
        {
            return _obj.getCreateTime();
        }
    }

    private class ExchangeDelegate implements BrokerSchema.ExchangeDelegate
    {
        private final ExchangeConfig _obj;

        public ExchangeDelegate(final ExchangeConfig obj)
        {
            _obj = obj;
        }

        public BrokerSchema.VhostObject getVhostRef()
        {
            return (BrokerSchema.VhostObject) adapt(_obj.getVirtualHost());
        }

        public String getName()
        {
            return _obj.getName();
        }

        public String getType()
        {
            return _obj.getType().getName().toString();
        }

        public Boolean getDurable()
        {
            return _obj.isDurable();
        }

        public Boolean getAutoDelete()
        {
            return _obj.isAutoDelete();
        }

        public BrokerSchema.ExchangeObject getAltExchange()
        {
            if(_obj.getAlternateExchange() != null)
            {
                return (BrokerSchema.ExchangeObject) adapt(_obj.getAlternateExchange());
            }
            else
            {
                return null;
            }
        }

        public Map getArguments()
        {
            return _obj.getArguments();
        }

        public Long getProducerCount()
        {
            // TODO
            return 0l;
        }

        public Long getProducerCountHigh()
        {
            // TODO
            return 0l;
        }

        public Long getProducerCountLow()
        {
            // TODO
            return 0l;
        }

        public Long getBindingCount()
        {
            return _obj.getBindingCount();
        }

        public Long getBindingCountHigh()
        {
            return _obj.getBindingCountHigh();
        }

        public Long getBindingCountLow()
        {
            // TODO
            return 0l;
        }

        public Long getMsgReceives()
        {
            return _obj.getMsgReceives();
        }

        public Long getMsgDrops()
        {
            return getMsgReceives() - getMsgRoutes();
        }

        public Long getMsgRoutes()
        {
            return _obj.getMsgRoutes();
        }

        public Long getByteReceives()
        {
            return _obj.getByteReceives();
        }

        public Long getByteDrops()
        {
            return getByteReceives() - getByteRoutes();
        }

        public Long getByteRoutes()
        {
            return _obj.getByteRoutes();
        }

        public UUID getId()
        {
            return _obj.getId();
        }

        public long getCreateTime()
        {
            return _obj.getCreateTime();
        }
    }

    private class QueueDelegate implements BrokerSchema.QueueDelegate
    {
        private final QueueConfig _obj;

        public QueueDelegate(final QueueConfig obj)
        {
            _obj = obj;
        }

        public BrokerSchema.VhostObject getVhostRef()
        {
            return (BrokerSchema.VhostObject)  adapt(_obj.getVirtualHost());
        }

        public String getName()
        {
            return _obj.getName();
        }

        public Boolean getDurable()
        {
            return _obj.isDurable();
        }

        public Boolean getAutoDelete()
        {
            return _obj.isAutoDelete();
        }

        public Boolean getExclusive()
        {
            return _obj.isExclusive();
        }

        public BrokerSchema.ExchangeObject getAltExchange()
        {
            if(_obj.getAlternateExchange() != null)
            {
                return (BrokerSchema.ExchangeObject) adapt(_obj.getAlternateExchange());
            }
            else
            {
                return null;
            }
        }

        public Long getMsgTotalEnqueues()
        {
            return _obj.getReceivedMessageCount();
        }

        public Long getMsgTotalDequeues()
        {
            return _obj.getMessageDequeueCount();
        }

        public Long getMsgTxnEnqueues()
        {
            return _obj.getMsgTxnEnqueues();
        }

        public Long getMsgTxnDequeues()
        {
            return _obj.getMsgTxnDequeues();
        }

        public Long getMsgPersistEnqueues()
        {
            return _obj.getPersistentMsgEnqueues();
        }

        public Long getMsgPersistDequeues()
        {
            return _obj.getPersistentMsgDequeues();
        }

        public Long getMsgDepth()
        {
            return (long) _obj.getMessageCount();
        }

        public Long getByteDepth()
        {
            return _obj.getQueueDepth();
        }

        public Long getByteTotalEnqueues()
        {
            return _obj.getTotalEnqueueSize();
        }

        public Long getByteTotalDequeues()
        {
            return _obj.getTotalDequeueSize();
        }

        public Long getByteTxnEnqueues()
        {
            return _obj.getByteTxnEnqueues();
        }

        public Long getByteTxnDequeues()
        {
            return _obj.getByteTxnDequeues();
        }

        public Long getBytePersistEnqueues()
        {
            return _obj.getPersistentByteEnqueues();
        }

        public Long getBytePersistDequeues()
        {
            return _obj.getPersistentByteDequeues();
        }

        public Long getConsumerCount()
        {
            return (long) _obj.getConsumerCount();
        }

        public Long getConsumerCountHigh()
        {
            return (long) _obj.getConsumerCountHigh();
        }

        public Long getConsumerCountLow()
        {
            // TODO
            return 0l;
        }

        public Long getBindingCount()
        {
            return (long) _obj.getBindingCount();
        }

        public Long getBindingCountHigh()
        {
            return (long) _obj.getBindingCountHigh();
        }

        public Long getBindingCountLow()
        {
            // TODO
            return 0l;
        }

        public Long getUnackedMessages()
        {
            return _obj.getUnackedMessageCount();
        }

        public Long getUnackedMessagesHigh()
        {
            return _obj.getUnackedMessageCountHigh();
        }

        public Long getUnackedMessagesLow()
        {
            // TODO
            return 0l;
        }

        public Long getMessageLatencySamples()
        {
            // TODO
            return 0l;
        }

        public Long getMessageLatencyMin()
        {
            // TODO
            return 0l;
        }

        public Long getMessageLatencyMax()
        {
            // TODO
            return 0l;
        }

        public Long getMessageLatencyAverage()
        {
            // TODO
            return 0l;
        }

        public Boolean getFlowStopped()
        {
            return Boolean.FALSE;
        }

        public Long getFlowStoppedCount()
        {
            return 0L;
        }

        public BrokerSchema.QueueClass.PurgeMethodResponseCommand purge(final BrokerSchema.QueueClass.PurgeMethodResponseCommandFactory factory,
                                                                        final Long request)
        {
            try
            {
                _obj.purge(request);
            } catch (AMQException e)
            {
                // TODO
                throw new RuntimeException();
            }
            return factory.createResponseCommand();
        }

        public BrokerSchema.QueueClass.RerouteMethodResponseCommand reroute(final BrokerSchema.QueueClass.RerouteMethodResponseCommandFactory factory, 
                                                                            final Long request, 
                                                                            final Boolean useAltExchange, 
                                                                            final String exchange)
        {
            //TODO
            return factory.createResponseCommand(CompletionCode.NOT_IMPLEMENTED);
        }


        public Map getArguments()
        {
            return _obj.getArguments();
        }

        public UUID getId()
        {
            return _obj.getId();
        }

        public long getCreateTime()
        {
            return _obj.getCreateTime();
        }
    }

    private class BindingDelegate implements BrokerSchema.BindingDelegate
    {
        private final BindingConfig _obj;

        public BindingDelegate(final BindingConfig obj)
        {
            _obj = obj;
        }

        public BrokerSchema.ExchangeObject getExchangeRef()
        {
            return (BrokerSchema.ExchangeObject) adapt(_obj.getExchange());
        }

        public BrokerSchema.QueueObject getQueueRef()
        {
            return (BrokerSchema.QueueObject) adapt(_obj.getQueue());
        }

        public String getBindingKey()
        {
            return _obj.getBindingKey();
        }

        public Map getArguments()
        {
            return _obj.getArguments();
        }

        public String getOrigin()
        {
            return _obj.getOrigin();
        }

        public Long getMsgMatched()
        {
            // TODO
            return _obj.getMatches();
        }

        public UUID getId()
        {
            return _obj.getId();
        }

        public long getCreateTime()
        {
            return _obj.getCreateTime();
        }
    }

    private class ConnectionDelegate implements BrokerSchema.ConnectionDelegate
    {
        private final ConnectionConfig _obj;

        public ConnectionDelegate(final ConnectionConfig obj)
        {
            _obj = obj;
        }

        public BrokerSchema.VhostObject getVhostRef()
        {
            return (BrokerSchema.VhostObject)  adapt(_obj.getVirtualHost());
        }

        public String getAddress()
        {
            return _obj.getAddress();
        }

        public Boolean getIncoming()
        {
            return _obj.isIncoming();
        }

        public Boolean getSystemConnection()
        {
            return _obj.isSystemConnection();
        }

        public Boolean getFederationLink()
        {
            return _obj.isFederationLink();
        }

        public String getAuthIdentity()
        {
            return _obj.getAuthId();
        }

        public String getRemoteProcessName()
        {
            return _obj.getRemoteProcessName();
        }

        public Long getRemotePid()
        {
            Integer remotePID = _obj.getRemotePID();
            return remotePID == null ? null : (long) remotePID;
        }

        public Long getRemoteParentPid()
        {
            Integer remotePPID = _obj.getRemoteParentPID();
            return remotePPID == null ? null : (long) remotePPID;

        }

        public Boolean getClosing()
        {
            return false;
        }

        public Long getFramesFromClient()
        {
            // TODO
            return 0l;
        }

        public Long getFramesToClient()
        {
            // TODO
            return 0l;
        }

        public Long getBytesFromClient()
        {
            // TODO
            return 0l;
        }

        public Long getBytesToClient()
        {
            // TODO
            return 0l;
        }

        public Long getMsgsFromClient()
        {
            // TODO
            return 0l;
        }

        public Long getMsgsToClient()
        {
            // TODO
            return 0l;
        }

        public BrokerSchema.ConnectionClass.CloseMethodResponseCommand close(final BrokerSchema.ConnectionClass.CloseMethodResponseCommandFactory factory)
        {
            _obj.mgmtClose();
            
            return factory.createResponseCommand();
        }

        public UUID getId()
        {
            return _obj.getId();
        }

        public long getCreateTime()
        {
            return _obj.getCreateTime();
        }

        public Boolean getShadow()
        {
            return _obj.isShadow();
        }
        
        public Boolean getUserProxyAuth()
        {
            // TODO
            return false;
        }
    }

    private class SessionDelegate implements BrokerSchema.SessionDelegate
    {
        private final SessionConfig _obj;

        public SessionDelegate(final SessionConfig obj)
        {
            _obj = obj;
        }

        public BrokerSchema.VhostObject getVhostRef()
        {
            return (BrokerSchema.VhostObject) adapt(_obj.getVirtualHost());
        }

        public String getName()
        {
            return _obj.getSessionName();
        }

        public Integer getChannelId()
        {
            return _obj.getChannel();
        }

        public BrokerSchema.ConnectionObject getConnectionRef()
        {
            return (BrokerSchema.ConnectionObject) adapt(_obj.getConnectionConfig());
        }

        public Long getDetachedLifespan()
        {
            return _obj.getDetachedLifespan();
        }

        public Boolean getAttached()
        {
            return _obj.isAttached();
        }

        public Long getExpireTime()
        {
            return _obj.getExpiryTime();
        }

        public Long getMaxClientRate()
        {
            return _obj.getMaxClientRate();
        }

        public Long getFramesOutstanding()
        {
            // TODO
            return 0l;
        }

        public Long getTxnStarts()
        {
            return _obj.getTxnStarts();
        }

        public Long getTxnCommits()
        {
            return _obj.getTxnCommits();
        }

        public Long getTxnRejects()
        {
            return _obj.getTxnRejects();
        }

        public Long getTxnCount()
        {
            return _obj.getTxnCount();
        }

        public Long getClientCredit()
        {
            // TODO
            return 0l;
        }

        public BrokerSchema.SessionClass.SolicitAckMethodResponseCommand solicitAck(final BrokerSchema.SessionClass.SolicitAckMethodResponseCommandFactory factory)
        {
            //TODO
            return factory.createResponseCommand(CompletionCode.NOT_IMPLEMENTED);
        }

        public BrokerSchema.SessionClass.DetachMethodResponseCommand detach(final BrokerSchema.SessionClass.DetachMethodResponseCommandFactory factory)
        {
            //TODO
            return factory.createResponseCommand(CompletionCode.NOT_IMPLEMENTED);
        }

        public BrokerSchema.SessionClass.ResetLifespanMethodResponseCommand resetLifespan(final BrokerSchema.SessionClass.ResetLifespanMethodResponseCommandFactory factory)
        {
            //TODO
            return factory.createResponseCommand(CompletionCode.NOT_IMPLEMENTED);
        }

        public BrokerSchema.SessionClass.CloseMethodResponseCommand close(final BrokerSchema.SessionClass.CloseMethodResponseCommandFactory factory)
        {
            try
            {
                _obj.mgmtClose();
            }
            catch (AMQException e)
            {
                return factory.createResponseCommand(CompletionCode.EXCEPTION, e.getMessage());
            }

            return factory.createResponseCommand();
        }

        public UUID getId()
        {
            return _obj.getId();
        }

        public long getCreateTime()
        {
            return _obj.getCreateTime();
        }
    }

    private class SubscriptionDelegate implements BrokerSchema.SubscriptionDelegate
    {
        private final SubscriptionConfig _obj;

        private SubscriptionDelegate(final SubscriptionConfig obj)
        {
            _obj = obj;
        }


        public BrokerSchema.SessionObject getSessionRef()
        {
            return (BrokerSchema.SessionObject) adapt(_obj.getSessionConfig());
        }

        public BrokerSchema.QueueObject getQueueRef()
        {
            return (BrokerSchema.QueueObject) adapt(_obj.getQueue());
        }

        public String getName()
        {
            return _obj.getName();
        }

        public Boolean getBrowsing()
        {
            return _obj.isBrowsing();
        }

        public Boolean getAcknowledged()
        {
            return _obj.isExplicitAcknowledge();
        }

        public Boolean getExclusive()
        {
            return _obj.isExclusive();
        }

        public String getCreditMode()
        {
            return _obj.getCreditMode();
        }

        public Map getArguments()
        {
            return _obj.getArguments();
        }

        public Long getDelivered()
        {
            return _obj.getDelivered();
        }

        public UUID getId()
        {
            return _obj.getId();
        }

        public long getCreateTime()
        {
            return _obj.getCreateTime();
        }
    }

        private class BridgeDelegate implements BrokerSchema.BridgeDelegate
        {
            private final BridgeConfig _obj;

            private BridgeDelegate(final BridgeConfig obj)
            {
                _obj = obj;
            }

            public BrokerSchema.LinkObject getLinkRef()
            {
                return (BrokerSchema.LinkObject) adapt(_obj.getLink());
            }

            public Integer getChannelId()
            {
                return _obj.getChannelId();
            }

            public Boolean getDurable()
            {
                return _obj.isDurable();
            }

            public String getSrc()
            {
                return _obj.getSource();
            }

            public String getDest()
            {
                return _obj.getDestination();
            }

            public String getKey()
            {
                return _obj.getKey();
            }

            public Boolean getSrcIsQueue()
            {
                return _obj.isQueueBridge();
            }

            public Boolean getSrcIsLocal()
            {
                return _obj.isLocalSource();
            }

            public String getTag()
            {
                return _obj.getTag();
            }

            public String getExcludes()
            {
                return _obj.getExcludes();
            }

            public Boolean getDynamic()
            {
                return _obj.isDynamic();
            }

            public Integer getSync()
            {
                return _obj.getAckBatching();
            }

            public BrokerSchema.BridgeClass.CloseMethodResponseCommand close(final BrokerSchema.BridgeClass.CloseMethodResponseCommandFactory factory)
            {
                return null;
            }

            public UUID getId()
            {
                return _obj.getId();
            }

            public long getCreateTime()
            {
                return _obj.getCreateTime();
            }
        }

    private class LinkDelegate implements BrokerSchema.LinkDelegate
    {
        private final LinkConfig _obj;

        private LinkDelegate(final LinkConfig obj)
        {
            _obj = obj;
        }

        public BrokerSchema.VhostObject getVhostRef()
        {
            return (BrokerSchema.VhostObject) adapt(_obj.getVirtualHost());
        }

        public String getHost()
        {
            return _obj.getHost();
        }

        public Integer getPort()
        {
            return _obj.getPort();
        }

        public String getTransport()
        {
            return _obj.getTransport();
        }

        public Boolean getDurable()
        {
            return _obj.isDurable();
        }

        public String getState()
        {
            // TODO
            return "";
        }

        public String getLastError()
        {
            // TODO
            return "";
        }

        public BrokerSchema.LinkClass.CloseMethodResponseCommand close(final BrokerSchema.LinkClass.CloseMethodResponseCommandFactory factory)
        {
            _obj.close();
            return factory.createResponseCommand();
        }

        public BrokerSchema.LinkClass.BridgeMethodResponseCommand bridge(final BrokerSchema.LinkClass.BridgeMethodResponseCommandFactory factory,
                                                                         final Boolean durable,
                                                                         final String src,
                                                                         final String dest,
                                                                         final String key,
                                                                         final String tag,
                                                                         final String excludes,
                                                                         final Boolean srcIsQueue,
                                                                         final Boolean srcIsLocal,
                                                                         final Boolean dynamic,
                                                                         final Integer sync)
        {
            _obj.createBridge(durable, dynamic, srcIsQueue, srcIsLocal, src, dest, key, tag, excludes);
            return factory.createResponseCommand();
        }

        public UUID getId()
        {
            return _obj.getId();
        }

        public long getCreateTime()
        {
            return _obj.getCreateTime();
        }
    }

}
