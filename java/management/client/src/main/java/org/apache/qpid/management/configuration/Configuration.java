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
package org.apache.qpid.management.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

import org.apache.qpid.management.Messages;
import org.apache.qpid.management.Names;
import org.apache.qpid.management.domain.handler.base.IMessageHandler;
import org.apache.qpid.management.domain.handler.impl.InvocationResult;
import org.apache.qpid.management.domain.model.AccessMode;
import org.apache.qpid.management.domain.model.type.Type;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.ReplyTo;
import org.apache.qpid.transport.util.Logger;

/**
 * Qpid Management bridge configuration.
 * Basically iy is a singleton that is holding all the configurtion data loaded at startup.
 * 
 * @author Andrea Gazzarini
 */
public final class Configuration
{    
    private final static Logger LOGGER = Logger.get(Configuration.class);
    private static Configuration INSTANCE = new Configuration();
    
    Map<Integer, Type> _typeMappings = new HashMap<Integer,Type>();
    Map<Integer,AccessMode> _accessModes = new HashMap<Integer, AccessMode>();
    Map<Type,String> _validators = new HashMap<Type, String>();
    
    Map<UUID,BrokerConnectionData> _brokerConnectionInfos = new HashMap<UUID, BrokerConnectionData>();
    
    Map<Character, String> _managementQueueHandlers = new HashMap<Character, String>();
    Map<Character, String> _methodReplyQueueHandlers = new HashMap<Character, String>();
    
    private String _managementQueueName;
    private String _methodReplyQueueName;
    
    private Header _headerForCommandMessages;
    private DeliveryProperties _deliveryProperties = new DeliveryProperties();
    private MessageProperties _messageProperties = new MessageProperties();
    public BlockingQueue<InvocationResult> _resultExchangeChannel = new SynchronousQueue<InvocationResult>();
    
    // Private constructor.
    private Configuration()
    {
        defineQueueNames();
        createHeaderForCommandMessages();
    }

    /**
     * Returns the singleton instance.
     * 
     * @return the singleton instance.
     */
    public static Configuration getInstance ()
    {
        return INSTANCE;
    }  
    
    /**
     * Returns true if this configuration has at least one broker connection data.
     * 
     * @return true if this configuration has at least one broker connection data.
     */
    public boolean hasOneOrMoreBrokersDefined()
    {
    	return !_brokerConnectionInfos.isEmpty();
    }
    
    /**
     * Returns the type associated to the given code.
     * 
     * @param code the code used as search criteria.
     * @return the type associated to the given code.
     * @throws UnknownTypeCodeException when the given code is not associated to any type.
     */
    public Type getType(int code) throws UnknownTypeCodeException 
    {
        Type result = _typeMappings.get(code);
        if (result == null) 
        {
            throw new UnknownTypeCodeException(code);
        }
        return result;
    }
    
    /**
     * Returns the access mode associated to the given code.
     * 
     * @param code the code used as search criteria.
     * @return the access mode associated to the given code.
     * @throws UnknownAccessCodeException when the given code is not associated to any access mode.
     */
    public AccessMode getAccessMode(int code) throws UnknownAccessCodeException
    {
        AccessMode result = _accessModes.get(code);
        if (result == null) 
        {
            throw new UnknownAccessCodeException(code);
        }
        return result;
    }

    /**
     * Returns the validator class name associated to the given type.
     * 
     * @param type the type. 
     * @return the validator class name associated to the given type.
     */
    public String getValidatorClassName (Type type)
    {
        return _validators.get(type);
    }
    
    /**
     * Gets from this configuration the list of known broker (I mean, only their connection data).
     * 
     * @return the list of known broker 
     */
    public Set<Entry<UUID, BrokerConnectionData>> getConnectionInfos(){
        return _brokerConnectionInfos.entrySet();
    }

    /**
     * Gets from this configuration the connection data of the broker associated with the given id.
     * 
     * @param brokerId the broker identifier.
     * @return the connection data of the broker associated with the given id.
     * @throws UnknownBrokerException when the given id is not associated with any broker.
     */
    public BrokerConnectionData getBrokerConnectionData (UUID brokerId) throws UnknownBrokerException
    {
        BrokerConnectionData connectionData = _brokerConnectionInfos.get(brokerId);
        if (connectionData == null)
        {
            throw new UnknownBrokerException(brokerId);
        }
        return _brokerConnectionInfos.get(brokerId);
    }
    
    /**
     * Returns the name of the management queue.
     *  
     * @return the name of the management queue.
     */
    public String getManagementQueueName() {
        return _managementQueueName;
    }
    
    /**
     * Returns the name of the method-reply queue.
     * 
     * @return the name of the method-reply queue.
     */
    public String getMethodReplyQueueName() {
        return _methodReplyQueueName;
    }
    
    /**
     * Returns a map containing all the configured management message handlers.
     * A management message handler it is a basically a processor for a management queue incoming message associated 
     * with a specific opcode.
     * 
     * @return a map containing all the configured management message handlers.
     */
    public Map<Character, IMessageHandler> getManagementQueueHandlers() 
    {
        Map<Character, IMessageHandler> result = new HashMap<Character, IMessageHandler>();    
        
        for (Entry<Character, String> entry : _managementQueueHandlers.entrySet())
        {
            Character opcode = entry.getKey();
            String className = entry.getValue();
            try 
            {
                result.put(opcode, (IMessageHandler)Class.forName(className).newInstance());
            } catch(Exception exception) 
            {
                LOGGER.error(exception,Messages.QMAN_100008_MANAGEMENT_MESSAGE_HANDLER_NOT_AVAILABLE,opcode);
            }
        }
        return result;
    }

    /**
     * Returns a map containing all the configured method-reply message handlers.
     * A management message handler it is a basically a processor for a method-reply queue incoming message associated 
     * with a specific opcode.
     * 
     * @return a map containing all the configured method-reply  message handlers.
     */
    public Map<Character, IMessageHandler> getMethodReplyQueueHandlers() 
    {
        Map<Character, IMessageHandler> result = new HashMap<Character, IMessageHandler>();
       
        for (Entry<Character, String> entry : _methodReplyQueueHandlers.entrySet())
        {
            Character opcode = entry.getKey();
            String className = entry.getValue();
            try 
            {
                result.put(opcode, (IMessageHandler)Class.forName(className).newInstance());
            } catch(Exception exception) 
            {
                LOGGER.error(exception,Messages.QMAN_100009_METHOD_REPLY_MESSAGE_HANDLER_NOT_AVAILABLE,opcode);
            }
        }
        return result;
    }

    /**
     * Returns the message header used for sending command message on management queue.
     * 
     * @return the message header used for sending command message on management queue.
     */
    public Header getCommandMessageHeader ()
    {
        return _headerForCommandMessages;
    }

    /**
     * Returns the command message properties.
     *  
     * @return the command message properties.
     */
    public MessageProperties getCommandMessageProperties ()
    {
        return _messageProperties;
    }

    /**
     * Returns the command message delivery properties.
     *  
     * @return the command message delivery properties.
     */
    public DeliveryProperties getCommandDeliveryProperties ()
    {
        return _deliveryProperties;
    }        
    
    /**
     * Adds a new type mapping to this configuration.
     * 
     * @param mapping the type mapping that will be added.
     */
    void addTypeMapping(TypeMapping mapping) {
        int code = mapping.getCode();
        Type type = mapping.getType();
        String validatorClassName = mapping.getValidatorClassName();
        _typeMappings.put(code, type);
        _validators.put(type, validatorClassName);
        
        LOGGER.info(Messages.QMAN_000005_TYPE_MAPPING_CONFIGURED, code,type,validatorClassName);
    }
    
    /**
     * Adds a new access mode mapping to this configuration.
     * 
     * @param mapping the mapping that will be added.
     */
    void addAccessModeMapping(AccessModeMapping mapping){
        int code = mapping.getCode();
        AccessMode accessMode = mapping.getAccessMode();
        _accessModes.put(code, accessMode);
        
        LOGGER.info(Messages.QMAN_000006_ACCESS_MODE_MAPPING_CONFIGURED, code,accessMode);        
    }    
    
    /**
     * Adds a new management message handler to this configuration.
     * The incoming mapping object will contains an opcode and the class (as a string) of the message handler that will be used
     * for processing incoming messages with that opcode.
     * 
     * @param mapping the message handler mapping.
     */
    void addManagementMessageHandlerMapping (MessageHandlerMapping mapping)
    {
        Character opcode = mapping.getOpcode();
        String handlerClass = mapping.getMessageHandlerClass();
        _managementQueueHandlers.put(opcode, handlerClass);
        
        LOGGER.info(Messages.QMAN_000007_MANAGEMENT_HANDLER_MAPPING_CONFIGURED, opcode,handlerClass); 
    }

    /**
     * Adds a new method-reply message handler to this configuration.
     * The incoming mapping object will contains an opcode and the class (as a string) of the message handler that will be used
     * for processing incoming messages with that opcode.
     * 
     * @param mapping the message handler mapping.
     */
    void addMethodReplyMessageHandlerMapping (MessageHandlerMapping mapping)
    {
        Character opcode = mapping.getOpcode();
        String handlerClass = mapping.getMessageHandlerClass();
        _methodReplyQueueHandlers.put(opcode, handlerClass);
        
        LOGGER.info(Messages.QMAN_000008_METHOD_REPLY_HANDLER_MAPPING_CONFIGURED, opcode,handlerClass);     
    }
    
    /**
     * Adds to this configuration a new broker connection data.
     * 
     * @param brokerId the broker identifier.
     * @param connectionData the connection data.
     * @throws BrokerAlreadyConnectedException when the broker is already connected.
     * @throws BrokerConnectionException when a connection cannot be estabilished.
     */
    void addBrokerConnectionData (UUID brokerId, BrokerConnectionData connectionData) throws BrokerAlreadyConnectedException, BrokerConnectionException 
    {
    	if (_brokerConnectionInfos.containsValue(connectionData))
    	{
    		throw new BrokerAlreadyConnectedException(connectionData);
    	}
    	
    	try 
    	{
        	QpidDatasource.getInstance().addConnectionPool(brokerId, connectionData);
            _brokerConnectionInfos.put(brokerId,connectionData);

            LOGGER.info(Messages.QMAN_000009_BROKER_DATA_CONFIGURED,brokerId,connectionData);        
    	} catch(Exception exception)
    	{
    		throw new BrokerConnectionException(exception);
    	}
      
    }
    
    /**
     * Header for command messages is created once because it only contains static values.
     */
    private void createHeaderForCommandMessages ()
    {
        ReplyTo replyTo=new ReplyTo();
        replyTo.setRoutingKey(_methodReplyQueueName);
        _messageProperties.setReplyTo(replyTo);
        _deliveryProperties.setRoutingKey(Names.AGENT_ROUTING_KEY);        
        _headerForCommandMessages = new Header(_deliveryProperties, _messageProperties);
    }
    
    /**
     * Creates the name of the queues used by this service. 
     * This is done because if a broker should be managed by one or more management client, then each of them
     * must have its own channels to communicate with.
     */
    private void defineQueueNames() 
    {
        UUID uuid = UUID.randomUUID();
        _managementQueueName = Names.MANAGEMENT_QUEUE_PREFIX+uuid;
        _methodReplyQueueName = Names.METHOD_REPLY_QUEUE_PREFIX+uuid;
        
        LOGGER.debug(Messages.QMAN_200004_MANAGEMENT_QUEUE_NAME,_managementQueueName);
        LOGGER.debug(Messages.QMAN_200005_METHOD_REPLY_QUEUE_NAME,_methodReplyQueueName);        
    }
}
