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
package org.apache.qpid.management.domain.model;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;

import org.apache.qpid.management.Protocol;
import org.apache.qpid.management.domain.handler.impl.MethodOrEventDataTransferObject;
import org.apache.qpid.management.domain.model.type.Binary;
import org.apache.qpid.management.domain.services.QpidService;
import org.apache.qpid.transport.codec.ManagementDecoder;
import org.apache.qpid.transport.codec.ManagementEncoder;
import org.apache.qpid.transport.util.Logger;

/**
 * Qpid Class definition.
 * A type definition for a manageable object.
 * This class is also responsible to manage incoming obejct instance data (configuration & instrumentation). 
 * How can we handle data before schema is injected into this class? simply we must retain that data in raw format.
 * This class has 2 states : 
 * 1) first state is when schema is not yet injected. In this case the incoming object data is retained as is (in raw format);
 * 2) second state is when schema is injected. In this case each injection of data will result in an update / create / delete of 
 * the corresponding object instance. In addition, the first time the state change, the old retained raw data is cnverted in 
 * object instance(s).
 * 
 * @author Andrea Gazzarini
 */
class QpidClass
{        
    /**
     * State interface for this class definition.
     * Each state is responsible to handle the injection of the object data. 
     * 
     * @author Andrea Gazzarini
     */
    interface State 
    {    
        /**
         * Adds configuration data for the object instance associated to the given object identifier.
         * 
         * @param objectId the object identifier.
         * @param rawData the raw configuration data.
         */
        void addInstrumentationData (Binary objectId, byte[] rawData);

        /**
         * Adds instrumentation data for the object instance associated to the given object identifier.
         * 
         * @param objectId the object identifier.
         * @param rawData the raw instrumentation data.
         */
        void addConfigurationData (Binary objectId, byte[] rawData);       
        
        /**
         * Inject the schema into this class definition.
         * 
         * @param propertyDefinitions
         * @param statisticDefinitions
         * @param methodDefinitions
         * @param eventDefinitions
         * @throws UnableToBuildFeatureException when it's not possibile to parse schema and build the class definition.
         */
        public  void  setSchema (
                List<Map<String, Object>> propertyDefinitions, 
                List<Map<String, Object>> statisticDefinitions,
                List<MethodOrEventDataTransferObject> methodDefinitions, 
                List<MethodOrEventDataTransferObject> eventDefinitions) throws UnableToBuildFeatureException;
    };
    
    /**
     * This is the initial state of every qpid class. 
     * The class definition instance is created but its schema has not been injected. 
     * Incoming configuration & instrumentation data will be stored in raw format because we don't know how to 
     * parse it until the schema arrives.
     * In addition, this state is responsible (when data arrives) to request its schema.
     */
    final State _schemaNotRequested = new State() {

        /**
         * Stores the incoming data in raw format and request the schema for this class.
         * After that a transition to the next state is made.
         * 
         * @param objectId the object instance identifier.
         * @param rawData incoming configuration data.
         */
        public synchronized void addConfigurationData (Binary objectId, byte[] rawData)
        {
            schemaRequest();
            QpidManagedObject instance = getObjectInstance(objectId,false);
            instance._rawConfigurationData.add(rawData);       
            _state = _schemaRequestedButNotYetInjected;
        }

        /**
         * Stores the incoming data in raw format and request the schema for this class.
         * After that a transition to the next state is made.
         * 
         * @param objectId the object instance identifier.
         * @param rawData incoming instrumentation data.
         */
        public synchronized void addInstrumentationData (Binary objectId, byte[] rawData)
        {
            schemaRequest();
            QpidManagedObject instance = getObjectInstance(objectId,false);
            instance._rawConfigurationData.add(rawData);
            _state = _schemaRequestedButNotYetInjected;
        }

        /**
         * This method only throws an illegal state exception because when a schema arrives 
         * this state is no longer valid.
         */
        public void setSchema (
                List<Map<String, Object>> propertyDefinitions,
                List<Map<String, Object>> statisticDefinitions, 
                List<MethodOrEventDataTransferObject> methodDefinitions,
                List<MethodOrEventDataTransferObject> eventDefinitions) throws UnableToBuildFeatureException
        {
            throw new IllegalStateException("When a schema arrives it's not possible for this class to be in this state.");
        }
    };
    
    /**
     * This is the first state of this class definition : the schema is not yet injected so each injection of object data will be 
     * retained in raw format.
     */
    final State _schemaRequestedButNotYetInjected = new State()
    {
        /**
         * Stores the incoming data in raw format.
         * 
         * @param objectId the object instance identifier.
         * @param rawData incoming configuration data.
         */
        public void addConfigurationData (Binary objectId, byte[] rawData)
        {
            QpidManagedObject instance = getObjectInstance(objectId,false);
            instance._rawConfigurationData.add(rawData);
        }

        /**
         * Stores the incoming data in raw format.
         * 
         * @param objectId the object instance identifier.
         * @param rawData incoming instrumentation data.
         */
        public void addInstrumentationData (Binary objectId, byte[] rawData)
        {
            QpidManagedObject instance = getObjectInstance(objectId,false);
            instance._rawInstrumentationData.add(rawData);
        }

        /**
         * When a schema is injected into this defintiion the following should happen :
         * 1) the incoming schema is parsed and the class definition is built;
         * 2) the retained raw data is converted into object instance(s)
         * 3) the internal state of this class changes;
         * 
         * If someting is wrong during that process the schema is not built and the state don't change.
         */
        public synchronized void setSchema (
                List<Map<String, Object>> propertyDefinitions,
                List<Map<String, Object>> statisticDefinitions, 
                List<MethodOrEventDataTransferObject> methodDefinitions,
                List<MethodOrEventDataTransferObject> eventDefinitions) throws UnableToBuildFeatureException
        {
                
                MBeanAttributeInfo [] attributesMetadata = new MBeanAttributeInfo[propertyDefinitions.size()+statisticDefinitions.size()];
                MBeanOperationInfo [] operationsMetadata = new MBeanOperationInfo[methodDefinitions.size()];
                
                buildAttributes(propertyDefinitions,statisticDefinitions,attributesMetadata);
                buildMethods(methodDefinitions,operationsMetadata);
                buildEvents(eventDefinitions);
                
                _metadata = new MBeanInfo(_name,_name,attributesMetadata,null,operationsMetadata,null);
            
                // Converting stored object instances into JMX MBean and removing raw instance data.
                for (Entry<Binary, QpidManagedObject> instanceEntry : _objectInstances.entrySet())
                {
                    Binary objectId = instanceEntry.getKey();
                    QpidManagedObject instance = instanceEntry.getValue();
                    
                    for (Iterator<byte[]> iterator = instance._rawInstrumentationData.iterator(); iterator.hasNext();)
                    {
                        updateInstanceWithInstrumentationData(instance,iterator.next());
                        iterator.remove();
                    }

                    for (Iterator<byte[]> iterator = instance._rawConfigurationData.iterator(); iterator.hasNext();)
                    {
                        updateInstanceWithConfigurationData(instance, iterator.next());
                        iterator.remove();
                    }

                    JMX_SERVICE.registerObjectInstance(instance,_parent.getOwnerId(),_parent.getName(),_name,objectId);
                }
            _state = _schemaInjected;
        }
    };
    
    /**
     * After a schema is built into this definition this is the current state of the class.
     */
    final State _schemaInjected = new State()
    {
        /**
         * Updates the configuration state of the object instance associates with the given object identifier.
         * 
         * @param objectId the object identifier.
         * @param rawData the configuration data (raw format).
         */
        public void addConfigurationData (Binary objectId, byte[] rawData)
        {
            QpidManagedObject instance = getObjectInstance(objectId,true);            
            updateInstanceWithConfigurationData(instance, rawData);
        }

        /**
         * Updates the instrumentation state of the object instance associates with the given object identifier.
         * 
         * @param objectId the object identifier.
         * @param rawData the instrumentation data (raw format).
         */
        public void addInstrumentationData (Binary objectId, byte[] rawData)
        {
            QpidManagedObject instance = getObjectInstance(objectId,true);            
            updateInstanceWithInstrumentationData(instance, rawData);
        }

        /**
         * Never called when the class definition has this state.
         */
        public void setSchema (
                List<Map<String, Object>> propertyDefinitions,
                List<Map<String, Object>> statisticDefinitions, 
                List<MethodOrEventDataTransferObject> methodDefinitions,
                List<MethodOrEventDataTransferObject> eventDefinitions) throws UnableToBuildFeatureException
        {
            throw new IllegalStateException("When a schema arrives it's not possible for this class to be in this state.");
        }
    };
    
    /**
     * MBean used for representing remote broker object instances.
     * This is the core component of the QMan domain model
     * 
     * @author Andrea Gazzarini
     */
    class QpidManagedObject implements DynamicMBean,MBeanRegistration
    {
        // After this mbean is registered with the MBean server this collection holds the mbean attributes
        private Map<String,Object> _attributes = new HashMap<String, Object>();
        private Binary _objectId;
        
        // Arrays used for storing raw data before this mbean is registered to mbean server.
        List<byte[]> _rawInstrumentationData = new ArrayList<byte[]>();
        List<byte[]>  _rawConfigurationData = new ArrayList<byte[]>();
        
        /**
         * Builds a new managed object with the given object identifier.
         * 
         * @param objectId the object identifier.
         */
        QpidManagedObject(Binary objectId)
        {
            this._objectId = objectId;
        }

        /**
         * Creates or replace the given attribute.
         * Note that this is not part of the management interface of this object instance and therefore will be accessible only
         * from within this class.
         * It is used to update directly the object attributes.
         * 
         * @param attributeName the name of the attribute.
         * @param property newValue the new value of the attribute.
         */
        void createOrReplaceAttributeValue(String attributeName, Object newValue) 
        {
            _attributes.put(attributeName, newValue);
        }
        
        /**
         * Returns the value of the given attribute.s
         * 
         * @throws AttributeNotFoundException when no attribute is found with the given name.
         */
        public Object getAttribute (String attributeName) throws AttributeNotFoundException, MBeanException, ReflectionException
        {
            if (attributeName == null) 
            {
                throw new RuntimeOperationsException(new IllegalArgumentException("attribute name must not be null"));
            }
            
            if (_properties.containsKey(attributeName) || _statistics.containsKey(attributeName))
            {
                return _attributes.get(attributeName);  
            } else 
            {
                throw new AttributeNotFoundException(attributeName);
            }        
        }

       /**
        * Get the values of several attributes of the Dynamic MBean.
        *
        * @param attributes A list of the attributes to be retrieved.
        *
        * @return  The list of attributes retrieved.
        */
        public AttributeList getAttributes (String[] attributes)
        {
            if (attributes == null) 
            {
                throw new RuntimeOperationsException(new IllegalArgumentException("attributes array must not be null"));
            }
            
            AttributeList result = new AttributeList(attributes.length);
            for (int i = 0; i < attributes.length; i++)
            {
                String attributeName = attributes[i];
                try 
                {
                    result.add(new Attribute(attributeName,getAttribute(attributeName)));
                } catch(Exception exception) 
                {
                    // Already logged.
                }
            } 
            return result;
        }

        /**
         * Returns metadata for this object instance.
         */
        // Developer Note : note that this metadata is a member of the outer class definition : in that way we create 
        // that metadata only once and then it will be shared between all object instances (it's a readonly object)
        public MBeanInfo getMBeanInfo ()
        {
            return _metadata;
        }

        /**
         * Executes an operation on this object instance.
         * 
         * @param actionName the name of the method.
         * @param params the method parameters 
         * @param signature the method signature.
         */
        public Object invoke (String actionName, Object[] params, String[] signature) throws MBeanException,ReflectionException
        {
            // TODO : Overloaded methods
            QpidMethod method = _methods.get(actionName);
            if (method != null) 
            {
                try
                {
                    methodRequest(_objectId, method, params);
                    return null;
                } catch (ValidationException exception)
                {
                    throw new MBeanException(exception);
                }
            } else {
                throw new ReflectionException(new NoSuchMethodException(actionName));
            }
        }

        /**
         * Sets the value of the given attribute on this object instance.
         * 
         * @param attribute contains the new value of the attribute.
         * @throws AttributeNotFoundException when the given attribute is not found on this object instance.
         * @throws InvalidAttributeValueException when the given value is violating one attribute invariant.
         */
        public void setAttribute (Attribute attribute) throws AttributeNotFoundException,
                InvalidAttributeValueException, MBeanException, ReflectionException
        {
            QpidProperty property = _properties.get(attribute.getName());
            try 
            {
                property.validate(attribute.getValue());
            } catch(ValidationException exception) 
            {
                throw new InvalidAttributeValueException(exception.getMessage());
            }
            throw new RuntimeException("Not yet implemented.");
        }

        /**
         * Sets the values of several attributes of this MBean.
         *
         * @param attributes a list of attributes: The identification of the attributes to be set and the values they are to be set to.
         * @return  The list of attributes that were set, with their new values.
         */
        public AttributeList setAttributes (AttributeList attributes)
        {
            throw new RuntimeException("Not yet implemented.");
        }

        /**
         * MBean server callback after deregistration.
         */
        public void postDeregister ()
        {
        }

        /**
         * After the object is registered the raw data is set to null.
         * This is done because we no longer need this data : it has already been 
         * injected into this object instance.
         * 
         * @param registrationDone a flag indicating if the instance has been registered to mbean server.
         */
        public void postRegister (Boolean registrationDone)
        {
            if (registrationDone) 
            {
                _rawConfigurationData = null;
                _rawInstrumentationData = null;
            }
        }

        /**
         * MBean server callback before deregistration.
         */
        public void preDeregister () throws Exception
        {
        }
        
        /**
         * MBean server callback before registration.
         */
        public ObjectName preRegister (MBeanServer server, ObjectName name) throws Exception
        {
            return name;
        }
    }

    private final static Logger LOGGER = Logger.get(QpidClass.class);
    private final static JmxService JMX_SERVICE = new JmxService();
        
    private final String _name;
    private final Binary _hash;
    
    private final QpidPackage _parent;
    
    Map<String, QpidProperty> _properties = new HashMap<String, QpidProperty>(); 
    Map<String, QpidStatistic> _statistics = new HashMap<String, QpidStatistic>();
    private Map<String, QpidMethod> _methods = new HashMap<String, QpidMethod>();
    
    private List<QpidProperty> _schemaOrderedProperties = new ArrayList<QpidProperty>();
    private List<QpidStatistic> _schemaOrderedStatistics= new ArrayList<QpidStatistic>();
    private MBeanInfo _metadata;

    private final QpidService _service;
    
    private int _howManyPresenceBitMasks;
    
    Map<Binary, QpidManagedObject> _objectInstances = new HashMap<Binary, QpidManagedObject>();
    State _state = _schemaNotRequested;;
    
    /**
     * Builds a new class with the given name and package as parent.
     * 
     * @param className the name of the class.
     * @param hash the class schema hash.
     * @param parentPackage the parent of this class.
     */
    QpidClass(String className, Binary hash, QpidPackage parentPackage)
    {
        this._name = className;
        this._parent = parentPackage;
        this._hash = hash;
        this._service = new QpidService(_parent.getOwnerId());
        LOGGER.debug(
                "<QMAN-200017> : Class definition has been built (without schema) for %s::%s.%s", 
                _parent.getOwnerId(),
                _parent.getName(),
                _name);        
    }
    
    /**
     * Adds the configuration data for the object instance associated to the given object identifier.
     * 
     * @param objectId the object identifier.
     * @param rawData the raw configuration data.
     */
    void addInstrumentationData (Binary objectId, byte[] rawData)
    {
        LOGGER.debug("<QMAN-200015> : Incoming instrumentation data for %s::%s.%s.%s",
                _parent.getOwnerId(),
                _parent.getName(),
                _name,
                objectId);        
        _state.addInstrumentationData(objectId, rawData);
    }
    
    /**
     * Adds the instrumentation data for the object instance associated to the given object identifier.
     * 
     * @param objectId the object identifier.
     * @param rawData the raw instrumentation data.
     */
    void addConfigurationData (Binary objectId, byte[] rawData)
    {
        LOGGER.debug("<QMAN-200016> : Incoming configuration data for %s::%s.%s.%s",
                _parent.getOwnerId(),
                _parent.getName(),
                _name,
                objectId);        
        _state.addConfigurationData(objectId, rawData);
    }

    /**
     * Sets the schema for this class definition. 
     * A schema is basically a metadata description of all properties, statistics, methods and events of this class.
     * 
     * @param propertyDefinitions properties metadata.
     * @param statisticDefinitions statistics metadata.
     * @param methodDefinitions methods metadata.
     * @param eventDefinitions events metadata.
     * @throws UnableToBuildFeatureException when some error occurs while parsing the incoming schema.
     */
     void setSchema (
            List<Map<String, Object>> propertyDefinitions, 
            List<Map<String, Object>> statisticDefinitions,
            List<MethodOrEventDataTransferObject> methodDefinitions, 
            List<MethodOrEventDataTransferObject> eventDefinitions) throws UnableToBuildFeatureException
    {
         LOGGER.info("<QMAN-000012> : Incoming schema for %s::%s.%s",_parent.getOwnerId(),_parent.getName(),_name);
        _state.setSchema(propertyDefinitions, statisticDefinitions, methodDefinitions, eventDefinitions);
    }    

    /**
     * Internal method used for building attributes definitions.
     * 
     * @param props the map contained in the properties schema.
     * @param stats the map contained in the statistics schema.
     * @param attributes the management metadata for attributes.
     * @throws UnableToBuildFeatureException  when it's not possibile to build one attribute definition.
     */
    void buildAttributes (
            List<Map<String, Object>> props,
            List<Map<String, Object>> stats,
            MBeanAttributeInfo[] attributes) throws UnableToBuildFeatureException
    {
        int index = 0;
        int howManyOptionalProperties = 0;
        
        for (Map<String, Object> propertyDefinition : props)
        {
            QpidFeatureBuilder builder = QpidFeatureBuilder.createPropertyBuilder(propertyDefinition);
            builder.build();
            
            QpidProperty property = (QpidProperty) builder.getQpidFeature();           
            
            howManyOptionalProperties += (property.isOptional()) ? 1 : 0;
            
            _properties.put(property.getName(),property);
            _schemaOrderedProperties.add(property);
            attributes[index++]=(MBeanAttributeInfo) builder.getManagementFeature();
            
            LOGGER.debug(
                    "<QMAN-200017> : Property definition for %s::%s.%s has been built.",
                    _parent.getName(),
                    _name,
                    property);
        }
                
        _howManyPresenceBitMasks =  (int)Math.ceil((double)howManyOptionalProperties / 8);
        
        LOGGER.debug(
                "<QMAN-200018> : Class %s::%s.%s has %s optional properties.",
                _parent.getOwnerId(),
                _parent.getName(),
                _name,
                _howManyPresenceBitMasks);
        
        for (Map<String, Object> statisticDefinition : stats)
        {
            QpidFeatureBuilder builder = QpidFeatureBuilder.createStatisticBuilder(statisticDefinition);
            builder.build();
            QpidStatistic statistic = (QpidStatistic) builder.getQpidFeature();
            
            _statistics.put(statistic.getName(),statistic);
            _schemaOrderedStatistics.add(statistic);
            attributes[index++]=(MBeanAttributeInfo) builder.getManagementFeature();
            
            LOGGER.debug(
                    "<QMAN-200019> : Statistic definition for %s::%s.%s has been built.",
                    _parent.getName(),
                    _name,
                    statistic);            
        }
    }    
    
    /**
     * Returns the object instance associated to the given identifier.
     * Note that if the identifier is not associated to any obejct instance, a new one will be created.
     * 
     * @param objectId the object identifier.
     * @param registration a flag indicating whenever the (new ) instance must be registered with MBean server.
     * @return the object instance associated to the given identifier.
     */
    QpidManagedObject getObjectInstance(Binary objectId, boolean registration) 
    {
        QpidManagedObject objectInstance = _objectInstances.get(objectId);
        if (objectInstance == null) 
        {
            objectInstance = new QpidManagedObject(objectId);
            _objectInstances.put(objectId, objectInstance);
            if (registration)
            {
                JMX_SERVICE.registerObjectInstance(objectInstance,_parent.getOwnerId(),_parent.getName(),_name,objectId);
            }
        }
        return objectInstance;
    }
    
    /**
     * Internal method used for building event statistics defintions.
     * 
     * @param definitions the properties map contained in the incoming schema.
     * @throws UnableToBuildFeatureException  when it's not possibile to build one or more definitions.
     */
    void buildEvents (List<MethodOrEventDataTransferObject> eventDefinitions)
    {
        // TODO
    }    
    
    /**
     * Internal method used for building method defintiions.
     * 
     * @param definitions the properties map contained in the incoming schema.
     * @param operationsMetadata 
     * @throws UnableToBuildFeatureException  when it's not possibile to build one or more definitions.
     */
    void buildMethods (List<MethodOrEventDataTransferObject> definitions, MBeanOperationInfo[] operationsMetadata) throws UnableToBuildFeatureException
    {
        int index = 0;
        for (MethodOrEventDataTransferObject definition: definitions)
        {
            QpidFeatureBuilder builder = QpidFeatureBuilder.createMethodBuilder(definition);
            builder.build();
            operationsMetadata [index++]= (MBeanOperationInfo) builder.getManagementFeature(); 
            QpidMethod method = (QpidMethod) builder.getQpidFeature();
            _methods.put(method.getName(),method);
        }
    }    
    
    private void schemaRequest()
    {
        ByteBuffer buffer = ByteBuffer.allocate(100);
        ManagementEncoder encoder = new ManagementEncoder(buffer);
        buffer.put(Protocol.SCHEMA_REQUEST_FIRST_FOUR_BYTES);
        
        // TODO
        encoder.writeSequenceNo(1000);
        encoder.writeStr8(_parent.getName());
        encoder.writeStr8(_name);
        _hash.encode(encoder);
        buffer.rewind();        
        try
        {
            _service.connect();
            _service.sendCommandMessage(buffer);
            _service.sync();
        } catch (Exception exception)
        {
            exception.printStackTrace();
            // TODO
            // Log.logSchemaRequestNotSent(exception,
            // _parent.getOwnerId(),_parent.getName(), _name);
        } finally
        {
            _service.close();
        }                
    }

    /**
     * Header (opcode='M') 
     * ObjectId of target object (128 bits) 
     * Package name (str8) 
     * Class name (str8) 
     * Class hash (bin128) 
     * Method name (str8) [as defined in the schema] 
     * Now encode all input ("I") and i/o (IO) arguments in the order in which they are defined in the schema. 
     * (i.e. make one pass over the argument list and encode arguments that are either input or inptu/output). 

     * @param objectId
     * @param method
     * @param parameters
     * @throws ValidationException
     */
    private void methodRequest(Binary objectId,QpidMethod method,Object [] parameters) throws ValidationException
    {
        ByteBuffer buffer = ByteBuffer.allocate(1000);
        ManagementEncoder encoder = new ManagementEncoder(buffer);
        buffer.put(Protocol.METHOD_REQUEST_FIRST_FOUR_BYTES);
        encoder.writeSequenceNo(0);
        objectId.encode(encoder);
        encoder.writeStr8(_parent.getName());
        encoder.writeStr8(_name);
        _hash.encode(encoder);
        encoder.writeStr8(method.getName());
        method.encodeParameters(parameters,encoder);
        
        buffer.rewind();        
          try
        {
            _service.connect();
            _service.sendCommandMessage(buffer);
            //_service.sync();
        } catch (Exception exception)
        {
            exception.printStackTrace();
            // TODO
            // Log.logSchemaRequestNotSent(exception,
            // _parent.getOwnerId(),_parent.getName(), _name);
        } finally
        {
            _service.close();
        }                
    }
    
    /**
     * Updates the given obejct instance with the given incoming configuration data.
     * 
     * @param instance the managed object instance.
     * @param rawData the incoming configuration data which contains new values for instance properties.
     */
    private void updateInstanceWithConfigurationData(QpidManagedObject instance,byte [] rawData)
    {
        ManagementDecoder decoder = new ManagementDecoder();
        decoder.init(ByteBuffer.wrap(rawData));

        byte [] presenceBitMasks = decoder.readBytes(_howManyPresenceBitMasks);
        for (QpidProperty property : _schemaOrderedProperties)
        {                  
            try {
                Object value = property.decodeValue(decoder,presenceBitMasks);
                instance.createOrReplaceAttributeValue(property.getName(),value);             
            } catch(Exception ignore) {
                LOGGER.error("Unable to decode value for %s::%s::%s", _parent.getName(),_name,property.getName());
            }
        }
    }
    
    /**
     * Updates the given object instance with the given incoming instrumentation data.
     * 
     * @param instance the managed object instance.
     * @param rawData the incoming instrumentation data which contains new values for instance properties.
     */
    private void updateInstanceWithInstrumentationData(QpidManagedObject instance,byte [] rawData)
    {
        ManagementDecoder decoder = new ManagementDecoder();
        decoder.init(ByteBuffer.wrap(rawData));

        for (QpidStatistic statistic : _schemaOrderedStatistics)
        {                  
            try {
                Object value = statistic.decodeValue(decoder);
                instance.createOrReplaceAttributeValue(statistic.getName(),value);             
            } catch(Exception ignore) {
                LOGGER.error("Unable to decode value for %s::%s::%s", _parent.getName(),_name,statistic.getName());
            }
        }
    }    
    
    @Override
    public String toString ()
    {
        return new StringBuilder()
            .append(_parent.getOwnerId())
            .append("::")
            .append(_parent.getName())
            .append("::")
            .append(_name)
            .toString();
    }

    /**
     * Removes the object instance associated to the given identifier.
     * 
     * @param objectId the object identifier.
     */
    void removeObjectInstance (Binary objectId)
    {
        QpidManagedObject toBeRemoved = _objectInstances.remove(objectId);
        if (toBeRemoved != null)
        {
            JMX_SERVICE.unregisterObjectInstance(_parent.getOwnerId(),_parent.getName(),_name,toBeRemoved._objectId);
        }
    }

    /**
     * Deregisters all the object instances and release all previously acquired resources.
     */
    void releaseResources ()
    {
        for (Iterator<Binary> iterator = _objectInstances.keySet().iterator(); iterator.hasNext();)
        {
            Binary objectId = iterator.next();
            JMX_SERVICE.unregisterObjectInstance(_parent.getOwnerId(),_parent.getName(),_name,objectId);
            iterator.remove();
            LOGGER.debug(
                    "%s.%s.%s object instance has been removed from broker %s",
                    _parent.getName(),
                    _name,objectId,
                    _parent.getOwnerId());
        }
        _service.close();
    }
}