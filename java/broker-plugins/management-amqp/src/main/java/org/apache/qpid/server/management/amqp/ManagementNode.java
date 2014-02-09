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
package org.apache.qpid.server.management.amqp;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.message.internal.InternalMessage;
import org.apache.qpid.server.message.internal.InternalMessageHeader;
import org.apache.qpid.server.model.AmqpManagement;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.plugin.SystemNodeCreator;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.MessageConverterRegistry;
import org.apache.qpid.server.security.AuthorizationHolder;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.StateChangeListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

class ManagementNode implements MessageSource<ManagementNodeConsumer,ManagementNode>, MessageDestination
{

    private final VirtualHost _virtualHost;

    private final UUID _id;

    private final CopyOnWriteArrayList<ConsumerRegistrationListener<ManagementNode>> _consumerRegistrationListeners =
            new CopyOnWriteArrayList<ConsumerRegistrationListener<ManagementNode>>();

    private final SystemNodeCreator.SystemNodeRegistry _registry;
    private final ConfiguredObject _managedObject;
    private Map<String, ManagementNodeConsumer> _consumers = new ConcurrentHashMap<String, ManagementNodeConsumer>();

    private Map<String,ManagedEntityType> _entityTypes = Collections.synchronizedMap(new LinkedHashMap<String, ManagedEntityType>());

    private Map<ManagedEntityType,Map<String,ConfiguredObject>> _entities = Collections.synchronizedMap(new LinkedHashMap<ManagedEntityType,Map<String,ConfiguredObject>>());


    public ManagementNode(final SystemNodeCreator.SystemNodeRegistry registry,
                          final ConfiguredObject configuredObject)
    {
        _virtualHost = registry.getVirtualHost();
        _registry = registry;
        final String name = configuredObject.getId() + "$management";
        _id = UUID.nameUUIDFromBytes(name.getBytes(Charset.defaultCharset()));


        _managedObject = configuredObject;

        populateTypeMetaData(configuredObject.getClass(), false);

        configuredObject.addChangeListener(new ModelObjectListener());

        final Class managementClass = getManagementClass(_managedObject.getClass());
        _entities.get(_entityTypes.get(managementClass.getName())).put(_managedObject.getName(), _managedObject);

        Collection<Class<? extends ConfiguredObject>> childClasses = Model.getInstance().getChildTypes(managementClass);
        for(Class<? extends ConfiguredObject> childClass : childClasses)
        {
            if(getManagementClass(childClass) != null)
            {
                for(ConfiguredObject child : _managedObject.getChildren(childClass))
                {
                    _entities.get(_entityTypes.get(getManagementClass(childClass).getName())).put(child.getName(), child);
                }
            }
        }

    }

    private Class getManagementClass(Class objectClass)
    {
        List<Class> allClasses = new ArrayList<Class>();
        allClasses.add(objectClass);
        allClasses.addAll(Arrays.asList(objectClass.getInterfaces()));
        allClasses.add(objectClass.getSuperclass());
        for(Class clazz : allClasses)
        {
            AmqpManagement annotation = (AmqpManagement) clazz.getAnnotation(AmqpManagement.class);
            if(annotation != null)
            {
                return clazz;
            }
        }
        return null;
    }

    private boolean populateTypeMetaData(final Class<? extends ConfiguredObject> objectClass, boolean allowCreate)
    {
        Class clazz = getManagementClass(objectClass);
        if( clazz != null)
        {
            AmqpManagement annotation = (AmqpManagement) clazz.getAnnotation(AmqpManagement.class);
            populateTypeMetaData(clazz, annotation);
            return true;
        }
        else
        {
            return false;
        }
    }

    private ManagedEntityType populateTypeMetaData(Class clazz,
                                                   final AmqpManagement entityType)
    {

        ManagedEntityType managedEntityType = _entityTypes.get(clazz.getName());

        if(managedEntityType == null)
        {
            List<String> opsList = new ArrayList<String>(Arrays.asList(entityType.operations()));
            if(entityType.creatable())
            {
                boolean isCreatableChild = false;
                for(Class<? extends ConfiguredObject> parentConfig : Model.getInstance().getParentTypes(clazz))
                {
                    isCreatableChild = parentConfig.isAssignableFrom(_managedObject.getClass());
                    if(isCreatableChild)
                    {
                        opsList.add("CREATE");
                        break;
                    }
                }
            }
            opsList.addAll(Arrays.asList("READ","UPDATE","DELETE"));

            Set<ManagedEntityType> parentSet = new HashSet<ManagedEntityType>();

            List<Class> allClasses = new ArrayList<Class>(Arrays.asList(clazz.getInterfaces()));
            if(clazz.getSuperclass() != null)
            {
                allClasses.add(clazz.getSuperclass());
            }

            for(Class parentClazz : allClasses)
            {
                if(parentClazz.getAnnotation(AmqpManagement.class) != null)
                {
                    ManagedEntityType parentType = populateTypeMetaData(parentClazz,
                                                                        (AmqpManagement) parentClazz.getAnnotation(
                                                                                AmqpManagement.class)
                                                                       );
                    parentSet.add(parentType);
                    parentSet.addAll(Arrays.asList(parentType.getParents()));

                }
            }
            managedEntityType = new ManagedEntityType(clazz.getName(), parentSet.toArray(new ManagedEntityType[parentSet.size()]), entityType.attributes(), opsList.toArray(new String[opsList.size()]));
            _entityTypes.put(clazz.getName(),managedEntityType);
            _entities.put(managedEntityType, Collections.synchronizedMap(new LinkedHashMap<String, ConfiguredObject>()));

            if(ConfiguredObject.class.isAssignableFrom(clazz))
            {
                Collection<Class<? extends ConfiguredObject>> childTypes = Model.getInstance().getChildTypes(clazz);
                for(Class<? extends ConfiguredObject> childClass : childTypes)
                {
                    populateTypeMetaData(childClass, true);
                }
            }

        }

        return managedEntityType;

    }

    @Override
    public  <M extends ServerMessage<? extends StorableMessageMetaData>> int send(final M message,
                    final InstanceProperties instanceProperties,
                    final ServerTransaction txn,
                    final Action<? super MessageInstance<?, ? extends Consumer>> postEnqueueAction)
    {

        @SuppressWarnings("unchecked")
        MessageConverter<M, InternalMessage> converter =
                MessageConverterRegistry.getConverter((Class<M>)message.getClass(), InternalMessage.class);

        final InternalMessage msg = converter.<M>convert(message, _virtualHost);

        if(validateMessage(msg))
        {
            txn.addPostTransactionAction(new ServerTransaction.Action()
            {
                @Override
                public void postCommit()
                {
                    enqueue(msg, instanceProperties, postEnqueueAction);
                }

                @Override
                public void onRollback()
                {

                }
            });

            return 1;
        }
        else
        {
            return 0;
        }
    }

    private boolean validateMessage(final ServerMessage message)
    {
        AMQMessageHeader header = message.getMessageHeader();
        return containsStringHeader(header, "type") && containsStringHeader(header, "operation")
               && (containsStringHeader(header,"name") || containsStringHeader(header,"identity"));
    }

    private boolean containsStringHeader(final AMQMessageHeader header, String name)
    {
        return header.containsHeader(name) && header.getHeader(name) instanceof String;
    }

    synchronized void enqueue(InternalMessage message, InstanceProperties properties, Action<? super MessageInstance<?, ? extends Consumer>> postEnqueueAction)
    {
        if(postEnqueueAction != null)
        {
            postEnqueueAction.performAction(new ConsumedMessageInstance(message, properties));
        }



        String name = (String) message.getMessageHeader().getHeader("name");
        String id = (String) message.getMessageHeader().getHeader("identity");
        String type = (String) message.getMessageHeader().getHeader("type");

        InternalMessage response;

        if("self".equals(name) && type.equals("org.amqp.management"))
        {
            response = performManagementOperation(message);
        }
        else
        {
            response = createFailureResponse(message);
        }



        for(ManagementNodeConsumer consumer : _consumers.values())
        {
            consumer.send(response);
        }

    }

    private InternalMessage performManagementOperation(final InternalMessage msg)
    {
        final InternalMessage responseMessage;
        final InternalMessageHeader requestHeader = msg.getMessageHeader();
        final MutableMessageHeader responseHeader = new MutableMessageHeader();
        responseHeader.setCorrelationId(requestHeader.getCorrelationId() == null
                                            ? requestHeader.getMessageId()
                                            : requestHeader.getCorrelationId());
        responseHeader.setMessageId(UUID.randomUUID().toString());


        String operation = (String) requestHeader.getHeader("operation");
        if("GET-TYPES".equals(operation))
        {
            responseMessage = performGetTypes(requestHeader, responseHeader);
        }
        else if("GET-ATTRIBUTES".equals(operation))
        {
            responseMessage = performGetAttributes(requestHeader, responseHeader);
        }
        else if("GET-OPERATIONS".equals(operation))
        {
            responseMessage = performGetOperations(requestHeader, responseHeader);
        }
        else if("QUERY".equals(operation))
        {
            responseMessage = performQuery(requestHeader, responseHeader);
        }
        else
        {
            responseMessage = InternalMessage.createBytesMessage(_virtualHost.getMessageStore(), requestHeader, new byte[0]);
        }
        return responseMessage;
    }

    private InternalMessage performGetTypes(final InternalMessageHeader requestHeader,
                                            final MutableMessageHeader responseHeader)
    {
        final InternalMessage responseMessage;
        List<String> restriction;
        if(requestHeader.containsHeader("entityTypes"))
        {
            restriction = (List<String>) requestHeader.getHeader("entityTypes");
        }
        else
        {
            restriction = null;
        }

        responseHeader.setHeader("statusCode", 200);
        Map<String,Object> responseMap = new LinkedHashMap<String, Object>();
        Map<String,ManagedEntityType> entityMapCopy;
        synchronized (_entityTypes)
        {
            entityMapCopy = new LinkedHashMap<String, ManagedEntityType>(_entityTypes);
        }

        for(ManagedEntityType type : entityMapCopy.values())
        {
            if(restriction == null || meetsIndirectRestriction(type,restriction))
            {
                final ManagedEntityType[] parents = type.getParents();
                List<String> parentNames = new ArrayList<String>();
                if(parents != null)
                {
                    for(ManagedEntityType parent : parents)
                    {
                        parentNames.add(parent.getName());
                    }
                }
                responseMap.put(type.getName(), parentNames);
            }
        }
        responseMessage = InternalMessage.createMapMessage(_virtualHost.getMessageStore(), responseHeader, responseMap);
        return responseMessage;
    }

    private InternalMessage performGetAttributes(final InternalMessageHeader requestHeader,
                                            final MutableMessageHeader responseHeader)
    {
        final InternalMessage responseMessage;
        List<String> restriction;
        if(requestHeader.containsHeader("entityTypes"))
        {
            restriction = (List<String>) requestHeader.getHeader("entityTypes");
        }
        else
        {
            restriction = null;
        }

        responseHeader.setHeader("statusCode", 200);
        Map<String,Object> responseMap = new LinkedHashMap<String, Object>();
        Map<String,ManagedEntityType> entityMapCopy;
        synchronized (_entityTypes)
        {
            entityMapCopy = new LinkedHashMap<String, ManagedEntityType>(_entityTypes);
        }

        for(ManagedEntityType type : entityMapCopy.values())
        {
            if(restriction == null || restriction.contains(type.getName()))
            {
                responseMap.put(type.getName(), Arrays.asList(type.getAttributes()));
            }
        }
        responseMessage = InternalMessage.createMapMessage(_virtualHost.getMessageStore(), responseHeader, responseMap);
        return responseMessage;
    }


    private InternalMessage performGetOperations(final InternalMessageHeader requestHeader,
                                                 final MutableMessageHeader responseHeader)
    {
        final InternalMessage responseMessage;
        List<String> restriction;
        if(requestHeader.containsHeader("entityTypes"))
        {
            restriction = (List<String>) requestHeader.getHeader("entityTypes");
        }
        else
        {
            restriction = null;
        }

        responseHeader.setHeader("statusCode", 200);
        Map<String,Object> responseMap = new LinkedHashMap<String, Object>();
        Map<String,ManagedEntityType> entityMapCopy;
        synchronized (_entityTypes)
        {
            entityMapCopy = new LinkedHashMap<String, ManagedEntityType>(_entityTypes);
        }

        for(ManagedEntityType type : entityMapCopy.values())
        {
            if(restriction == null || restriction.contains(type.getName()))
            {
                responseMap.put(type.getName(), Arrays.asList(type.getOperations()));
            }
        }
        responseMessage = InternalMessage.createMapMessage(_virtualHost.getMessageStore(), responseHeader, responseMap);
        return responseMessage;
    }

    private InternalMessage performQuery(final InternalMessageHeader requestHeader,
                                         final MutableMessageHeader responseHeader)
    {
        final InternalMessage responseMessage;
        List<String> restriction;
        List<String> attributes;
        int offset;
        int count;

        if(requestHeader.containsHeader("entityTypes"))
        {
            restriction = (List<String>) requestHeader.getHeader("entityTypes");
            responseHeader.setHeader("entityTypes", restriction);
        }
        else
        {
            restriction = new ArrayList<String>(_entityTypes.keySet());
        }


        if(requestHeader.containsHeader("attributes"))
        {
            attributes = (List<String>) requestHeader.getHeader("attributes");
        }
        else
        {
            LinkedHashMap<String,Void> attributeSet = new LinkedHashMap<String, Void>();
            for(String entityType : restriction)
            {
                ManagedEntityType type = _entityTypes.get(entityType);
                if(type != null)
                {
                    for(String attributeName : type.getAttributes())
                    {
                        attributeSet.put(attributeName, null);
                    }
                }
            }
            attributes = new ArrayList<String>(attributeSet.keySet());

        }

        if(requestHeader.containsHeader("offset"))
        {
            offset = ((Number) requestHeader.getHeader("offset")).intValue();
            responseHeader.setHeader("offset",offset);
        }
        else
        {
            offset = 0;
        }

        if(requestHeader.containsHeader("count"))
        {
            count = ((Number) requestHeader.getHeader("count")).intValue();
        }
        else
        {
            count = Integer.MAX_VALUE;
        }

        responseHeader.setHeader("attributes", attributes);

        responseHeader.setHeader("statusCode", 200);
        List<List<Object>> responseList = new ArrayList<List<Object>>();

        int rowNo = 0;
        for(String type : restriction)
        {
            ManagedEntityType entityType = _entityTypes.get(type);
            if(entityType != null)
            {
                Map<String, ConfiguredObject> entityMap = _entities.get(entityType);
                if(entityMap  != null)
                {
                    List<ConfiguredObject> entities;
                    synchronized(entityMap)
                    {
                        entities = new ArrayList<ConfiguredObject>(entityMap.values());
                    }
                    for(ConfiguredObject entity : entities)
                    {
                        if(rowNo++ >= offset)
                        {
                            Object[] attrValue = new Object[attributes.size()];
                            int col = 0;
                            for(String attr : attributes)
                            {
                                Object value;
                                if("type".equals(attr))
                                {
                                    value = entityType.getName();
                                }
                                else
                                {
                                    value = fixValue(entity.getAttribute(attr));
                                }
                                attrValue[col++] = value;
                            }
                            responseList.add(Arrays.asList(attrValue));
                        }
                        if(responseList.size()==count)
                        {
                            break;
                        }
                    }
                }
            }

            if(responseList.size()==count)
            {
                break;
            }
        }
        responseHeader.setHeader("count", count);
        responseMessage = InternalMessage.createListMessage(_virtualHost.getMessageStore(),
                                                            responseHeader,
                                                            responseList);
        return responseMessage;
    }

    private Object fixValue(final Object value)
    {
        Object fixedValue;
        if(value instanceof Enum)
        {
            fixedValue = value.toString();
        }
        else if(value instanceof Map)
        {
            Map<Object, Object> oldValue = (Map<Object, Object>) value;
            Map<Object, Object> newValue = new LinkedHashMap<Object, Object>();
            for(Map.Entry<Object, Object> entry : oldValue.entrySet())
            {
                newValue.put(fixValue(entry.getKey()),fixValue(entry.getValue()));
            }
            fixedValue = newValue;
        }
        else if(value instanceof Collection)
        {
            Collection oldValue = (Collection) value;
            List newValue = new ArrayList(oldValue.size());
            for(Object o : oldValue)
            {
                newValue.add(fixValue(o));
            }
            fixedValue = newValue;
        }
        else if(value != null && value.getClass().isArray() && !(value instanceof byte[]))
        {
            fixedValue = fixValue(Arrays.asList((Object[])value));
        }
        else
        {
            fixedValue = value;
        }
        return fixedValue;

    }


    private boolean meetsIndirectRestriction(final ManagedEntityType type, final List<String> restriction)
    {
        if(restriction.contains(type.getName()))
        {
            return true;
        }
        if(type.getParents() != null)
        {
            for(ManagedEntityType parent : type.getParents())
            {
                if(meetsIndirectRestriction(parent, restriction))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private InternalMessage createFailureResponse(final InternalMessage msg)
    {
        final InternalMessageHeader requestHeader = msg.getMessageHeader();
        final Map<String,Object> appProps = new HashMap<String, Object>();
        final Map<Object, Object> body = new HashMap<Object, Object>();

        appProps.put("statusCode",200);
        body.put("prop1", "wibble");


        final InternalMessageHeader header =
                new InternalMessageHeader(appProps,
                                          requestHeader.getCorrelationId() == null
                                                  ? requestHeader.getMessageId()
                                                  : requestHeader.getCorrelationId(),
                                          0,
                                          null,
                                          null,
                                          UUID.randomUUID().toString(),
                                          null,
                                          null,
                                          (byte)4,
                                          System.currentTimeMillis(),
                                          null,
                                          null);


        return InternalMessage.createMapMessage(_virtualHost.getMessageStore(), header, body);
    }

    @Override
    public synchronized <T extends ConsumerTarget> ManagementNodeConsumer addConsumer(final T target,
                                final FilterManager filters,
                                final Class<? extends ServerMessage> messageClass,
                                final String consumerName,
                                final EnumSet<Consumer.Option> options) throws AMQException
    {

        final ManagementNodeConsumer managementNodeConsumer = new ManagementNodeConsumer(consumerName,this, target);
        target.consumerAdded(managementNodeConsumer);
        _consumers.put(consumerName, managementNodeConsumer);
        for(ConsumerRegistrationListener<ManagementNode> listener : _consumerRegistrationListeners)
        {
            listener.consumerAdded(this, managementNodeConsumer);
        }
        return managementNodeConsumer;
    }

    @Override
    public synchronized Collection<ManagementNodeConsumer> getConsumers()
    {
        return new ArrayList<ManagementNodeConsumer>(_consumers.values());
    }

    @Override
    public void addConsumerRegistrationListener(final ConsumerRegistrationListener<ManagementNode> listener)
    {
        _consumerRegistrationListeners.add(listener);
    }

    @Override
    public void removeConsumerRegistrationListener(final ConsumerRegistrationListener listener)
    {
        _consumerRegistrationListeners.remove(listener);
    }

    @Override
    public AuthorizationHolder getAuthorizationHolder()
    {
        return null;
    }

    @Override
    public void setAuthorizationHolder(final AuthorizationHolder principalHolder)
    {

    }

    @Override
    public void setExclusiveOwningSession(final AMQSessionModel owner)
    {

    }

    @Override
    public AMQSessionModel getExclusiveOwningSession()
    {
        return null;
    }

    @Override
    public boolean isExclusive()
    {
        return false;
    }

    @Override
    public String getName()
    {
        return "$management";
    }

    @Override
    public UUID getId()
    {
        return _id;
    }

    @Override
    public boolean isDurable()
    {
        return false;
    }

    private class ConsumedMessageInstance implements MessageInstance<ConsumedMessageInstance,Consumer>
    {
        private final ServerMessage _message;
        private final InstanceProperties _properties;

        public ConsumedMessageInstance(final ServerMessage message,
                                       final InstanceProperties properties)
        {
            _message = message;
            _properties = properties;
        }

        @Override
        public int getDeliveryCount()
        {
            return 0;
        }

        @Override
        public void incrementDeliveryCount()
        {

        }

        @Override
        public void decrementDeliveryCount()
        {

        }

        @Override
        public void addStateChangeListener(final StateChangeListener<? super ConsumedMessageInstance, State> listener)
        {

        }

        @Override
        public boolean removeStateChangeListener(final StateChangeListener<? super ConsumedMessageInstance, State> listener)
        {
            return false;
        }


        @Override
        public boolean acquiredByConsumer()
        {
            return false;
        }

        @Override
        public boolean isAcquiredBy(final Consumer consumer)
        {
            return false;
        }

        @Override
        public void setRedelivered()
        {

        }

        @Override
        public boolean isRedelivered()
        {
            return false;
        }

        @Override
        public Consumer getDeliveredConsumer()
        {
            return null;
        }

        @Override
        public void reject()
        {

        }

        @Override
        public boolean isRejectedBy(final Consumer consumer)
        {
            return false;
        }

        @Override
        public boolean getDeliveredToConsumer()
        {
            return true;
        }

        @Override
        public boolean expired() throws AMQException
        {
            return false;
        }

        @Override
        public boolean acquire(final Consumer sub)
        {
            return false;
        }

        @Override
        public int getMaximumDeliveryCount()
        {
            return 0;
        }

        @Override
        public int routeToAlternate(final Action<? super MessageInstance<?, ? extends Consumer>> action,
                                    final ServerTransaction txn)
        {
            return 0;
        }


        @Override
        public Filterable asFilterable()
        {
            return null;
        }

        @Override
        public boolean isAvailable()
        {
            return false;
        }

        @Override
        public boolean acquire()
        {
            return false;
        }

        @Override
        public boolean isAcquired()
        {
            return false;
        }

        @Override
        public void release()
        {

        }

        @Override
        public boolean resend() throws AMQException
        {
            return false;
        }

        @Override
        public void delete()
        {

        }

        @Override
        public boolean isDeleted()
        {
            return false;
        }

        @Override
        public ServerMessage getMessage()
        {
            return _message;
        }

        @Override
        public InstanceProperties getInstanceProperties()
        {
            return _properties;
        }

        @Override
        public TransactionLogResource getOwningResource()
        {
            return ManagementNode.this;
        }
    }

    private class ModelObjectListener implements ConfigurationChangeListener
    {
        @Override
        public void stateChanged(final ConfiguredObject object, final State oldState, final State newState)
        {
            if(newState == State.DELETED)
            {
                _registry.removeSystemNode(ManagementNode.this);
            }
        }

        @Override
        public void childAdded(final ConfiguredObject object, final ConfiguredObject child)
        {
            final ManagedEntityType entityType = _entityTypes.get(getManagementClass(child.getClass()).getName());
            if(entityType != null)
            {
                _entities.get(entityType).put(child.getName(), child);
            }
        }

        @Override
        public void childRemoved(final ConfiguredObject object, final ConfiguredObject child)
        {
            final ManagedEntityType entityType = _entityTypes.get(getManagementClass(child.getClass()).getName());
            if(entityType != null)
            {
                _entities.get(entityType).remove(child.getName());
            }
        }

        @Override
        public void attributeSet(final ConfiguredObject object,
                                 final String attributeName,
                                 final Object oldAttributeValue,
                                 final Object newAttributeValue)
        {

        }
    }

    private static class MutableMessageHeader implements AMQMessageHeader
    {
        private final LinkedHashMap<String, Object> _headers = new LinkedHashMap<String, Object>();
        private String _correlationId;
        private long _expiration;
        private String _userId;
        private String _appId;
        private String _messageId;
        private String _mimeType;
        private String _encoding;
        private byte _priority;
        private long _timestamp;
        private String _type;
        private String _replyTo;

        public void setCorrelationId(final String correlationId)
        {
            _correlationId = correlationId;
        }

        public void setExpiration(final long expiration)
        {
            _expiration = expiration;
        }

        public void setUserId(final String userId)
        {
            _userId = userId;
        }

        public void setAppId(final String appId)
        {
            _appId = appId;
        }

        public void setMessageId(final String messageId)
        {
            _messageId = messageId;
        }

        public void setMimeType(final String mimeType)
        {
            _mimeType = mimeType;
        }

        public void setEncoding(final String encoding)
        {
            _encoding = encoding;
        }

        public void setPriority(final byte priority)
        {
            _priority = priority;
        }

        public void setTimestamp(final long timestamp)
        {
            _timestamp = timestamp;
        }

        public void setType(final String type)
        {
            _type = type;
        }

        public void setReplyTo(final String replyTo)
        {
            _replyTo = replyTo;
        }

        public String getCorrelationId()
        {
            return _correlationId;
        }

        public long getExpiration()
        {
            return _expiration;
        }

        public String getUserId()
        {
            return _userId;
        }

        public String getAppId()
        {
            return _appId;
        }

        public String getMessageId()
        {
            return _messageId;
        }

        public String getMimeType()
        {
            return _mimeType;
        }

        public String getEncoding()
        {
            return _encoding;
        }

        public byte getPriority()
        {
            return _priority;
        }

        public long getTimestamp()
        {
            return _timestamp;
        }

        public String getType()
        {
            return _type;
        }

        public String getReplyTo()
        {
            return _replyTo;
        }

        @Override
        public Object getHeader(final String name)
        {
            return _headers.get(name);
        }

        @Override
        public boolean containsHeaders(final Set<String> names)
        {
            return _headers.keySet().containsAll(names);
        }

        @Override
        public boolean containsHeader(final String name)
        {
            return _headers.containsKey(name);
        }

        @Override
        public Collection<String> getHeaderNames()
        {
            return Collections.unmodifiableCollection(_headers.keySet());
        }

        public void setHeader(String header, Object value)
        {
            _headers.put(header,value);
        }

    }
}
