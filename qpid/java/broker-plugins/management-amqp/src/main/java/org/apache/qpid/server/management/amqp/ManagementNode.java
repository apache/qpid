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

import java.nio.charset.Charset;
import java.security.AccessControlException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.qpid.server.consumer.ConsumerImpl;
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
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectTypeRegistry;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.plugin.SystemNodeCreator;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.MessageConverterRegistry;
import org.apache.qpid.server.store.MessageDurability;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.StateChangeListener;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

class ManagementNode implements MessageSource, MessageDestination
{

    public static final String NAME_ATTRIBUTE = "name";
    public static final String IDENTITY_ATTRIBUTE = "identity";
    public static final String TYPE_ATTRIBUTE = "type";
    public static final String OPERATION_HEADER = "operation";
    public static final String SELF_NODE_NAME = "self";
    public static final String MANAGEMENT_TYPE = "org.amqp.management";
    public static final String GET_TYPES = "GET-TYPES";
    public static final String GET_ATTRIBUTES = "GET-ATTRIBUTES";
    public static final String GET_OPERATIONS = "GET-OPERATIONS";
    public static final String QUERY = "QUERY";
    public static final String ENTITY_TYPE_HEADER = "entityType";
    public static final String STATUS_CODE_HEADER = "statusCode";
    public static final int STATUS_CODE_OK = 200;
    public static final String ATTRIBUTES_HEADER = "attributes";
    public static final String OFFSET_HEADER = "offset";
    public static final String COUNT_HEADER = "count";
    public static final String MANAGEMENT_NODE_NAME = "$management";
    public static final String CREATE_OPERATION = "CREATE";
    public static final String READ_OPERATION = "READ";
    public static final String UPDATE_OPERATION = "UPDATE";
    public static final String DELETE_OPERATION = "DELETE";
    public static final String STATUS_DESCRIPTION_HEADER = "statusDescription";
    public static final int NOT_FOUND_STATUS_CODE = 404;
    public static final int NOT_IMPLEMENTED_STATUS_CODE = 501;
    public static final int STATUS_CODE_NO_CONTENT = 204;
    public static final int STATUS_CODE_FORBIDDEN = 403;
    public static final int STATUS_CODE_BAD_REQUEST = 400;
    public static final int STATUS_CODE_INTERNAL_ERROR = 500;
    public static final String ATTRIBUTE_NAMES = "attributeNames";
    public static final String RESULTS = "results";


    private final VirtualHostImpl _virtualHost;

    private final UUID _id;

    private final CopyOnWriteArrayList<ConsumerRegistrationListener<? super MessageSource>> _consumerRegistrationListeners =
            new CopyOnWriteArrayList<ConsumerRegistrationListener<? super MessageSource>>();

    private final SystemNodeCreator.SystemNodeRegistry _registry;
    private final ConfiguredObject<?> _managedObject;
    private Map<String, ManagementNodeConsumer> _consumers = new ConcurrentHashMap<String, ManagementNodeConsumer>();

    private Map<String,ManagedEntityType> _entityTypes = Collections.synchronizedMap(new LinkedHashMap<String, ManagedEntityType>());

    private Map<ManagedEntityType,Map<String,ConfiguredObject>> _entities = Collections.synchronizedMap(new LinkedHashMap<ManagedEntityType,Map<String,ConfiguredObject>>());


    public ManagementNode(final SystemNodeCreator.SystemNodeRegistry registry,
                          final ConfiguredObject<?> configuredObject)
    {
        _virtualHost = registry.getVirtualHost();
        _registry = registry;
        final String name = configuredObject.getId() + MANAGEMENT_NODE_NAME;
        _id = UUID.nameUUIDFromBytes(name.getBytes(Charset.defaultCharset()));


        _managedObject = configuredObject;

        configuredObject.addChangeListener(new ModelObjectListener());

    }

    private Class getManagementClass(Class objectClass)
    {

        if(objectClass.getAnnotation(ManagedObject.class)!=null)
        {
            return objectClass;
        }
        List<Class> allClasses = Collections.singletonList(objectClass);
        List<Class> testedClasses = new ArrayList<Class>();
        do
        {
            testedClasses.addAll( allClasses );
            allClasses = new ArrayList<Class>();
            for(Class c : testedClasses)
            {
                for(Class i : c.getInterfaces())
                {
                    if(!allClasses.contains(i))
                    {
                        allClasses.add(i);
                    }
                }
                if(c.getSuperclass() != null && !allClasses.contains(c.getSuperclass()))
                {
                    allClasses.add(c.getSuperclass());
                }
            }
            allClasses.removeAll(testedClasses);
            for(Class c : allClasses)
            {
                if(c.getAnnotation(ManagedObject.class) != null)
                {
                    return c;
                }
            }
        }
        while(!allClasses.isEmpty());
        return null;
    }

    private boolean populateTypeMetaData(final Class<? extends ConfiguredObject> objectClass, boolean allowCreate)
    {
        Class clazz = getManagementClass(objectClass);
        if( clazz != null)
        {
            ManagedObject annotation = (ManagedObject) clazz.getAnnotation(ManagedObject.class);
            populateTypeMetaData(clazz, annotation);
            return true;
        }
        else
        {
            return false;
        }
    }

    private ManagedEntityType populateTypeMetaData(Class clazz,
                                                   final ManagedObject entityType)
    {

        ManagedEntityType managedEntityType = _entityTypes.get(clazz.getName());

        if(managedEntityType == null)
        {
            List<String> opsList = new ArrayList<String>(Arrays.asList(entityType.operations()));
            if(entityType.creatable())
            {
                boolean isCreatableChild = false;
                Collection<Class<? extends ConfiguredObject>> parentTypes = _managedObject.getModel().getParentTypes(clazz);
                for(Class<? extends ConfiguredObject> parentConfig : parentTypes)
                {
                    isCreatableChild = parentConfig.isAssignableFrom(_managedObject.getClass());
                    if(isCreatableChild)
                    {
                        opsList.add(CREATE_OPERATION);
                        break;
                    }
                }
            }
            opsList.addAll(Arrays.asList(READ_OPERATION, UPDATE_OPERATION, DELETE_OPERATION));

            Set<ManagedEntityType> parentSet = new HashSet<ManagedEntityType>();

            List<Class> allClasses = new ArrayList<Class>(Arrays.asList(clazz.getInterfaces()));
            if(clazz.getSuperclass() != null)
            {
                allClasses.add(clazz.getSuperclass());
            }

            for(Class parentClazz : allClasses)
            {
                if(parentClazz.getAnnotation(ManagedObject.class) != null)
                {
                    ManagedEntityType parentType = populateTypeMetaData(parentClazz,
                                                                        (ManagedObject) parentClazz.getAnnotation(
                                                                                ManagedObject.class)
                                                                       );
                    parentSet.add(parentType);
                    parentSet.addAll(Arrays.asList(parentType.getParents()));

                }
            }
            managedEntityType = new ManagedEntityType(clazz.getName(), parentSet.toArray(new ManagedEntityType[parentSet.size()]),
                                                      (String[])(ConfiguredObjectTypeRegistry.getAttributeNames(
                                                              clazz).toArray(new String[0])),
                                                      opsList.toArray(new String[opsList.size()]));
            _entityTypes.put(clazz.getName(),managedEntityType);
            _entities.put(managedEntityType, Collections.synchronizedMap(new LinkedHashMap<String, ConfiguredObject>()));

            if(ConfiguredObject.class.isAssignableFrom(clazz))
            {
                Collection<Class<? extends ConfiguredObject>> childTypes = _managedObject.getModel().getChildTypes(clazz);
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
                                                                                  final String routingAddress,
                                                                                  final InstanceProperties instanceProperties,
                                                                                  final ServerTransaction txn,
                                                                                  final Action<? super MessageInstance> postEnqueueAction)
    {

        @SuppressWarnings("unchecked")
        MessageConverter converter =
                MessageConverterRegistry.getConverter(message.getClass(), InternalMessage.class);

        final InternalMessage msg = (InternalMessage) converter.convert(message, _virtualHost);

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
        return containsStringHeader(header, TYPE_ATTRIBUTE) && containsStringHeader(header, OPERATION_HEADER)
               && (containsStringHeader(header, NAME_ATTRIBUTE) || containsStringHeader(header, IDENTITY_ATTRIBUTE));
    }

    private boolean containsStringHeader(final AMQMessageHeader header, String name)
    {
        return header.containsHeader(name) && header.getHeader(name) instanceof String;
    }

    synchronized void enqueue(InternalMessage message, InstanceProperties properties, Action<? super MessageInstance> postEnqueueAction)
    {
        if(postEnqueueAction != null)
        {
            postEnqueueAction.performAction(new ConsumedMessageInstance(message, properties));
        }



        String name = (String) message.getMessageHeader().getHeader(NAME_ATTRIBUTE);
        String id = (String) message.getMessageHeader().getHeader(IDENTITY_ATTRIBUTE);
        String type = (String) message.getMessageHeader().getHeader(TYPE_ATTRIBUTE);
        String operation = (String) message.getMessageHeader().getHeader(OPERATION_HEADER);

        InternalMessage response;

        if(SELF_NODE_NAME.equals(name) && type.equals(MANAGEMENT_TYPE))
        {
            response = performManagementOperation(message);
        }
        else if(CREATE_OPERATION.equals(operation))
        {
            response = performCreateOperation(message, type);
        }
        else
        {

            ConfiguredObject entity = findSubject(name, id, type);

            if(entity != null)
            {
                response = performOperation(message, entity);
            }
            else
            {
                if(id != null)
                {
                    response = createFailureResponse(message,
                                                     NOT_FOUND_STATUS_CODE,
                                                     "No entity with id {0} of type {1} found", id, type);
                }
                else
                {
                    response = createFailureResponse(message,
                                                     NOT_FOUND_STATUS_CODE,
                                                     "No entity with name {0} of type {1} found", name, type);
                }
            }
        }


        ManagementNodeConsumer consumer = _consumers.get(message.getMessageHeader().getReplyTo());
        response.setInitialRoutingAddress(message.getMessageHeader().getReplyTo());
        if(consumer != null)
        {
            // TODO - check same owner
            consumer.send(response);
        }
        else
        {
            _virtualHost.getDefaultDestination().send(response,
                                                      message.getMessageHeader().getReplyTo(), InstanceProperties.EMPTY,
                                                      new AutoCommitTransaction(_virtualHost.getMessageStore()),
                                                      null);
        }
        // TODO - route to a queue

    }

    private InternalMessage performCreateOperation(final InternalMessage message, final String type)
    {
        InternalMessage response;
        ManagedEntityType entityType = _entityTypes.get(type);
        if(type != null)
        {
            if(Arrays.asList(entityType.getOperations()).contains(CREATE_OPERATION))
            {
                Object messageBody = message.getMessageBody();
                if(messageBody instanceof Map)
                {
                    try
                    {

                        Class<? extends ConfiguredObject> clazz =
                                (Class<? extends ConfiguredObject>) Class.forName(type);
                        try
                        {
                            ConfiguredObject child = _managedObject.createChild(clazz, (Map) messageBody);
                            if(child == null)
                            {
                                child = _entities.get(entityType).get(message.getMessageHeader().getHeader(NAME_ATTRIBUTE));
                            }
                            response = performReadOperation(message, child);
                        }
                        catch(AccessControlException e)
                        {
                            response = createFailureResponse(message, STATUS_CODE_FORBIDDEN, e.getMessage());
                        }
                    }
                    catch (ClassNotFoundException e)
                    {
                        response = createFailureResponse(message,
                                                         STATUS_CODE_INTERNAL_ERROR, "Unable to instantiate an instance of {0} ", type);
                    }
                }
                else
                {
                    response = createFailureResponse(message,
                                                     STATUS_CODE_BAD_REQUEST,
                                                     "The message body in the request was not of the correct type");
                }
            }
            else
            {
                response = createFailureResponse(message,
                                                 STATUS_CODE_FORBIDDEN,
                                                 "Cannot CREATE entities of type {0}", type);
            }
        }
        else
        {
            response = createFailureResponse(message,
                                             NOT_FOUND_STATUS_CODE,
                                             "Unknown type {0}",type);
        }
        return response;
    }

    private InternalMessage performOperation(final InternalMessage requestMessage, final ConfiguredObject entity)
    {
        String operation = (String) requestMessage.getMessageHeader().getHeader(OPERATION_HEADER);

        if(READ_OPERATION.equals(operation))
        {
            return performReadOperation(requestMessage, entity);
        }
        else if(DELETE_OPERATION.equals(operation))
        {
            return performDeleteOperation(requestMessage, entity);
        }
        else if(UPDATE_OPERATION.equals(operation))
        {
            return performUpdateOperation(requestMessage, entity);
        }
        else
        {
            return createFailureResponse(requestMessage, NOT_IMPLEMENTED_STATUS_CODE, "Unable to perform the {0} operation",operation);
        }
    }

    private InternalMessage performReadOperation(final InternalMessage requestMessage, final ConfiguredObject entity)
    {
        final InternalMessageHeader requestHeader = requestMessage.getMessageHeader();
        final MutableMessageHeader responseHeader = new MutableMessageHeader();
        responseHeader.setCorrelationId(requestHeader.getCorrelationId() == null
                                                ? requestHeader.getMessageId()
                                                : requestHeader.getCorrelationId());
        responseHeader.setMessageId(UUID.randomUUID().toString());
        responseHeader.setHeader(NAME_ATTRIBUTE, entity.getName());
        responseHeader.setHeader(IDENTITY_ATTRIBUTE, entity.getId().toString());
        responseHeader.setHeader(STATUS_CODE_HEADER,STATUS_CODE_OK);
        final String type = getManagementClass(entity.getClass()).getName();
        responseHeader.setHeader(TYPE_ATTRIBUTE, type);

        Map<String,Object> responseBody = new LinkedHashMap<String, Object>();
        final ManagedEntityType entityType = _entityTypes.get(type);
        for(String attribute : entityType.getAttributes())
        {
            responseBody.put(attribute, fixValue(entity.getAttribute(attribute)));
        }

        return InternalMessage.createMapMessage(_virtualHost.getMessageStore(),responseHeader, responseBody);
    }


    private InternalMessage performDeleteOperation(final InternalMessage requestMessage, final ConfiguredObject entity)
    {
        final InternalMessageHeader requestHeader = requestMessage.getMessageHeader();
        final MutableMessageHeader responseHeader = new MutableMessageHeader();
        responseHeader.setCorrelationId(requestHeader.getCorrelationId() == null
                                                ? requestHeader.getMessageId()
                                                : requestHeader.getCorrelationId());
        responseHeader.setMessageId(UUID.randomUUID().toString());
        responseHeader.setHeader(NAME_ATTRIBUTE, entity.getName());
        responseHeader.setHeader(IDENTITY_ATTRIBUTE, entity.getId().toString());
        final String type = getManagementClass(entity.getClass()).getName();
        responseHeader.setHeader(TYPE_ATTRIBUTE, type);
        try
        {
            entity.delete();
            responseHeader.setHeader(STATUS_CODE_HEADER, STATUS_CODE_NO_CONTENT);
        }
        catch(AccessControlException e)
        {
            responseHeader.setHeader(STATUS_CODE_HEADER, STATUS_CODE_FORBIDDEN);
        }

        return InternalMessage.createMapMessage(_virtualHost.getMessageStore(),responseHeader, Collections.emptyMap());
    }


    private InternalMessage performUpdateOperation(final InternalMessage requestMessage, final ConfiguredObject entity)
    {
        final InternalMessageHeader requestHeader = requestMessage.getMessageHeader();
        final MutableMessageHeader responseHeader = new MutableMessageHeader();
        responseHeader.setCorrelationId(requestHeader.getCorrelationId() == null
                                                ? requestHeader.getMessageId()
                                                : requestHeader.getCorrelationId());
        responseHeader.setMessageId(UUID.randomUUID().toString());
        responseHeader.setHeader(NAME_ATTRIBUTE, entity.getName());
        responseHeader.setHeader(IDENTITY_ATTRIBUTE, entity.getId().toString());
        final String type = getManagementClass(entity.getClass()).getName();
        responseHeader.setHeader(TYPE_ATTRIBUTE, type);

        Object messageBody = requestMessage.getMessageBody();
        if(messageBody instanceof Map)
        {
            try
            {
                entity.setAttributes((Map)messageBody);
                return performReadOperation(requestMessage, entity);
            }
            catch(AccessControlException e)
            {
                return createFailureResponse(requestMessage, STATUS_CODE_FORBIDDEN, e.getMessage());
            }
        }
        else
        {
            return createFailureResponse(requestMessage,
                                             STATUS_CODE_BAD_REQUEST,
                                             "The message body in the request was not of the correct type");
        }


    }

    private ConfiguredObject findSubject(final String name, final String id, final String type)
    {
        ConfiguredObject subject;
        ManagedEntityType met = _entityTypes.get(type);
        if(met == null)
        {
            return null;
        }

        subject = findSubject(name, id, met);
        if(subject == null)
        {
            ArrayList<ManagedEntityType> allTypes = new ArrayList<ManagedEntityType>(_entityTypes.values());
            for(ManagedEntityType entityType : allTypes)
            {
                if(Arrays.asList(entityType.getParents()).contains(met))
                {
                    subject = findSubject(name, id, entityType);
                    if(subject != null)
                    {
                        return subject;
                    }
                }
            }
        }
        return subject;
    }

    private ConfiguredObject findSubject(final String name, final String id, final ManagedEntityType entityType)
    {

        Map<String, ConfiguredObject> objects = _entities.get(entityType);
        if(name != null)
        {
            ConfiguredObject subject = objects.get(name);
            if(subject != null)
            {
                return subject;
            }
        }
        else
        {
            final Collection<ConfiguredObject> values = new ArrayList<ConfiguredObject>(objects.values());
            for(ConfiguredObject o : values)
            {
                if(o.getId().toString().equals(id))
                {
                    return o;
                }
            }
        }
        return null;
    }

    private InternalMessage createFailureResponse(final InternalMessage requestMessage,
                                       final int statusCode,
                                       final String stateDescription,
                                       String... params)
    {
        final InternalMessageHeader requestHeader = requestMessage.getMessageHeader();
        final MutableMessageHeader responseHeader = new MutableMessageHeader();
        responseHeader.setCorrelationId(requestHeader.getCorrelationId() == null
                                                ? requestHeader.getMessageId()
                                                : requestHeader.getCorrelationId());
        responseHeader.setMessageId(UUID.randomUUID().toString());
        for(String header : requestHeader.getHeaderNames())
        {
            responseHeader.setHeader(header, requestHeader.getHeader(header));
        }
        responseHeader.setHeader(STATUS_CODE_HEADER, statusCode);
        responseHeader.setHeader(STATUS_DESCRIPTION_HEADER, MessageFormat.format(stateDescription, params));
        return InternalMessage.createBytesMessage(_virtualHost.getMessageStore(), responseHeader, new byte[0]);

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


        String operation = (String) requestHeader.getHeader(OPERATION_HEADER);
        if(GET_TYPES.equals(operation))
        {
            responseMessage = performGetTypes(requestHeader, responseHeader);
        }
        else if(GET_ATTRIBUTES.equals(operation))
        {
            responseMessage = performGetAttributes(requestHeader, responseHeader);
        }
        else if(GET_OPERATIONS.equals(operation))
        {
            responseMessage = performGetOperations(requestHeader, responseHeader);
        }
        else if(QUERY.equals(operation))
        {
            responseMessage = performQuery(requestHeader, msg.getMessageBody(), responseHeader);
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
        if(requestHeader.containsHeader(ENTITY_TYPE_HEADER))
        {
            restriction = new ArrayList<String>(Collections.singletonList( (String)requestHeader.getHeader(ENTITY_TYPE_HEADER)));
        }
        else
        {
            restriction = null;
        }

        responseHeader.setHeader(STATUS_CODE_HEADER, STATUS_CODE_OK);
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
        String restriction;
        if(requestHeader.containsHeader(ENTITY_TYPE_HEADER))
        {
            restriction = (String) requestHeader.getHeader(ENTITY_TYPE_HEADER);
        }
        else
        {
            restriction = null;
        }

        responseHeader.setHeader(STATUS_CODE_HEADER, STATUS_CODE_OK);
        Map<String,Object> responseMap = new LinkedHashMap<String, Object>();
        Map<String,ManagedEntityType> entityMapCopy;
        synchronized (_entityTypes)
        {
            entityMapCopy = new LinkedHashMap<String, ManagedEntityType>(_entityTypes);
        }

        if(restriction == null)
        {
            for(ManagedEntityType type : entityMapCopy.values())
            {
                responseMap.put(type.getName(), Arrays.asList(type.getAttributes()));
            }
        }
        else if(entityMapCopy.containsKey(restriction))
        {
            responseMap.put(restriction, Arrays.asList(entityMapCopy.get(restriction).getAttributes()));
        }

        responseMessage = InternalMessage.createMapMessage(_virtualHost.getMessageStore(), responseHeader, responseMap);
        return responseMessage;
    }


    private InternalMessage performGetOperations(final InternalMessageHeader requestHeader,
                                                 final MutableMessageHeader responseHeader)
    {
        final InternalMessage responseMessage;
        String restriction;
        if(requestHeader.containsHeader(ENTITY_TYPE_HEADER))
        {
            restriction = (String) requestHeader.getHeader(ENTITY_TYPE_HEADER);
        }
        else
        {
            restriction = null;
        }

        responseHeader.setHeader(STATUS_CODE_HEADER, STATUS_CODE_OK);
        Map<String,Object> responseMap = new LinkedHashMap<String, Object>();
        Map<String,ManagedEntityType> entityMapCopy;
        synchronized (_entityTypes)
        {
            entityMapCopy = new LinkedHashMap<String, ManagedEntityType>(_entityTypes);
        }

        if(restriction == null)
        {
            for(ManagedEntityType type : entityMapCopy.values())
            {
                responseMap.put(type.getName(), Arrays.asList(type.getOperations()));
            }
        }
        else if(entityMapCopy.containsKey(restriction))
        {
            ManagedEntityType type = entityMapCopy.get(restriction);
            responseMap.put(type.getName(), Arrays.asList(type.getOperations()));
        }
        responseMessage = InternalMessage.createMapMessage(_virtualHost.getMessageStore(), responseHeader, responseMap);
        return responseMessage;
    }

    private InternalMessage performQuery(final InternalMessageHeader requestHeader,
                                         final Object messageBody, final MutableMessageHeader responseHeader)
    {
        final InternalMessage responseMessage;
        List<String> restriction;
        List<String> attributes;
        int offset;
        int count;

        if(requestHeader.containsHeader(ENTITY_TYPE_HEADER))
        {
            restriction = new ArrayList<String>(Collections.singletonList((String) requestHeader.getHeader(
                    ENTITY_TYPE_HEADER)));
            responseHeader.setHeader(ENTITY_TYPE_HEADER, restriction);
        }
        else
        {
            restriction = new ArrayList<String>(_entityTypes.keySet());
        }


        if(messageBody instanceof Map && ((Map)messageBody).get(ATTRIBUTE_NAMES) instanceof List)
        {
            attributes = (List<String>) ((Map)messageBody).get(ATTRIBUTE_NAMES);
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

        if(requestHeader.containsHeader(OFFSET_HEADER))
        {
            offset = ((Number) requestHeader.getHeader(OFFSET_HEADER)).intValue();
            responseHeader.setHeader(OFFSET_HEADER,offset);
        }
        else
        {
            offset = 0;
        }

        if(requestHeader.containsHeader(COUNT_HEADER))
        {
            count = ((Number) requestHeader.getHeader(COUNT_HEADER)).intValue();
        }
        else
        {
            count = Integer.MAX_VALUE;
        }


        responseHeader.setHeader(STATUS_CODE_HEADER, STATUS_CODE_OK);
        List<List<? extends Object>> responseList = new ArrayList<List<? extends Object>>();
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
                                if(TYPE_ATTRIBUTE.equals(attr))
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
                        if(responseList.size()==count+1)
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
        responseHeader.setHeader(COUNT_HEADER, responseList.size());
        Map<String,List> responseMap = new HashMap<String, List>();
        responseMap.put(ATTRIBUTE_NAMES, attributes);
        responseMap.put(RESULTS, responseList);
        responseMessage = InternalMessage.createMapMessage(_virtualHost.getMessageStore(),
                                                           responseHeader,
                                                           responseMap);
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

    @Override
    public synchronized  ManagementNodeConsumer addConsumer(final ConsumerTarget target,
                                final FilterManager filters,
                                final Class<? extends ServerMessage> messageClass,
                                final String consumerName,
                                final EnumSet<ConsumerImpl.Option> options)
    {

        final ManagementNodeConsumer managementNodeConsumer = new ManagementNodeConsumer(consumerName,this, target);
        target.consumerAdded(managementNodeConsumer);
        _consumers.put(consumerName, managementNodeConsumer);
        for(ConsumerRegistrationListener<? super MessageSource> listener : _consumerRegistrationListeners)
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
    public void addConsumerRegistrationListener(final ConsumerRegistrationListener<? super MessageSource> listener)
    {
        _consumerRegistrationListeners.add(listener);
    }

    @Override
    public void removeConsumerRegistrationListener(final ConsumerRegistrationListener listener)
    {
        _consumerRegistrationListeners.remove(listener);
    }

    @Override
    public boolean verifySessionAccess(final AMQSessionModel<?, ?> session)
    {
        return true;
    }

    @Override
    public String getName()
    {
        return MANAGEMENT_NODE_NAME;
    }

    @Override
    public UUID getId()
    {
        return _id;
    }

    @Override
    public MessageDurability getMessageDurability()
    {
        return MessageDurability.NEVER;
    }

    private class ConsumedMessageInstance implements MessageInstance
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
        public void addStateChangeListener(final StateChangeListener<? super MessageInstance, State> listener)
        {

        }

        @Override
        public boolean removeStateChangeListener(final StateChangeListener<? super MessageInstance, State> listener)
        {
            return false;
        }


        @Override
        public boolean acquiredByConsumer()
        {
            return false;
        }

        @Override
        public boolean isAcquiredBy(final ConsumerImpl consumer)
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
        public ConsumerImpl getDeliveredConsumer()
        {
            return null;
        }

        @Override
        public void reject()
        {

        }

        @Override
        public boolean isRejectedBy(final ConsumerImpl consumer)
        {
            return false;
        }

        @Override
        public boolean getDeliveredToConsumer()
        {
            return true;
        }

        @Override
        public boolean expired()
        {
            return false;
        }

        @Override
        public boolean acquire(final ConsumerImpl sub)
        {
            return false;
        }

        @Override
        public int getMaximumDeliveryCount()
        {
            return 0;
        }

        @Override
        public int routeToAlternate(final Action<? super MessageInstance> action,
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
        public boolean resend()
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
            else if(newState == State.ACTIVE && object instanceof org.apache.qpid.server.model.VirtualHost)
            {
                populateTypeMetaData(object.getClass(), false);
                final Class managementClass = getManagementClass(_managedObject.getClass());
                _entities.get(_entityTypes.get(managementClass.getName())).put(_managedObject.getName(), _managedObject);

                Collection<Class<? extends ConfiguredObject>> childClasses = object.getModel().getChildTypes(managementClass);
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
        }

        @Override
        public void childAdded(final ConfiguredObject object, final ConfiguredObject child)
        {
            final Class managementClass = getManagementClass(child.getClass());
            final ManagedEntityType entityType = _entityTypes.get(managementClass.getName());
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
