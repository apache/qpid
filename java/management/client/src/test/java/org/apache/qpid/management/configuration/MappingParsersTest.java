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

import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.management.TestConstants;
import org.apache.qpid.management.domain.handler.base.IMessageHandler;
import org.apache.qpid.management.domain.handler.impl.ConfigurationMessageHandler;
import org.apache.qpid.management.domain.handler.impl.InstrumentationMessageHandler;
import org.apache.qpid.management.domain.handler.impl.SchemaResponseMessageHandler;
import org.apache.qpid.management.domain.model.AccessMode;
import org.apache.qpid.management.domain.model.type.Type;
import org.apache.qpid.management.domain.model.type.Uint8;

/**
 * Test case for mapping parsers.
 * 
 * @author Andrea Gazzarini.
 */
public class MappingParsersTest extends TestCase
{
    /**
     * Tests the execution of the access mode mapping parser.
     * 
     * <br>precondition: An access mode mapping is built by the parser;
     * <br>postcondition: the corresponding access mode is available on the configuration.
     */
    public void testAccessModeMappingParser() throws UnknownAccessCodeException 
    {
        AccessModeMappingParser parser = new AccessModeMappingParser();
        parser.setCurrrentAttributeValue(String.valueOf(TestConstants.VALID_CODE));
        parser.setCurrentAttributeName(Tag.CODE.toString());
        parser.setCurrrentAttributeValue("RW");
        parser.setCurrentAttributeName(Tag.VALUE.toString());
        parser.setCurrentAttributeName(Tag.MAPPING.toString());
        
        AccessMode result = Configuration.getInstance().getAccessMode(TestConstants.VALID_CODE);
        assertEquals(AccessMode.RW,result);
    }    

    /**
     * Tests the execution of the broker connection data mapping parser.
     * 
     * <br>precondition: A broker connection datamapping is built by the parser;
     * <br>postcondition: the corresponding connection data is available on the configuration.
     */
    public void testBrokerConnectionDataParser() throws UnknownBrokerException 
    {
        String host = "127.0.0.1";
        String port = "7001";
        String virtualHost = "test";
        String username = "username_guest";
        String password ="password_guest";
        
        BrokerConnectionDataParser parser = new BrokerConnectionDataParser()
        {
            @Override
            UUID getUUId ()
            {
                return TestConstants.BROKER_ID;
            }
        };
        
        parser.setCurrrentAttributeValue(host);
        parser.setCurrentAttributeName(Tag.HOST.toString());
        parser.setCurrrentAttributeValue(port);
        parser.setCurrentAttributeName(Tag.PORT.toString());
        parser.setCurrrentAttributeValue(virtualHost);
        parser.setCurrentAttributeName(Tag.VIRTUAL_HOST.toString());
        parser.setCurrrentAttributeValue(username);
        parser.setCurrentAttributeName(Tag.USER.toString());
        parser.setCurrrentAttributeValue(password);
        parser.setCurrentAttributeName(Tag.PASSWORD.toString());
        parser.setCurrentAttributeName(Tag.BROKER.toString());
        
        BrokerConnectionData result = Configuration.getInstance().getBrokerConnectionData(TestConstants.BROKER_ID);
        
        assertEquals(host,result.getHost());
        assertEquals(Integer.parseInt(port),result.getPort());
        assertEquals(virtualHost,result.getVirtualHost());
        assertEquals(username,result.getUsername());
        assertEquals(password,result.getPassword());
    }

    /**
     * Tests the execution of the management queue handler mapping parser.
     * 
     * <br>precondition: Two managenent queue handlers mapping are built by the parser;
     * <br>postcondition: the corresponding management handlers are available on the configuration.
     */
    public void testManagementQueueMessageListenerParser() 
    {
        String instrOpcode = "i";
        String configOpCode = "c";
        
        ManagementQueueMessageListenerParser parser = new ManagementQueueMessageListenerParser();
        
        parser.setCurrrentAttributeValue(instrOpcode);
        parser.setCurrentAttributeName(Tag.OPCODE.toString());
        parser.setCurrrentAttributeValue(InstrumentationMessageHandler.class.getName());
        parser.setCurrentAttributeName(Tag.CLASS_NAME.toString());
        parser.setCurrentAttributeName(Tag.HANDLER.toString());

        parser.setCurrrentAttributeValue(configOpCode);
        parser.setCurrentAttributeName(Tag.OPCODE.toString());
        parser.setCurrrentAttributeValue(ConfigurationMessageHandler.class.getName());
        parser.setCurrentAttributeName(Tag.CLASS_NAME.toString());
        parser.setCurrentAttributeName(Tag.HANDLER.toString());

        
        Map<Character,IMessageHandler> result = Configuration.getInstance().getManagementQueueHandlers();
        
        assertEquals(2,result.size());
        
        assertEquals(InstrumentationMessageHandler.class,result.get(instrOpcode.charAt(0)).getClass());
        assertEquals(ConfigurationMessageHandler.class,result.get(configOpCode.charAt(0)).getClass());  
    }  
    
    /**
     * Tests the execution of the method-reply queue handler mapping parser.
     * 
     * <br>precondition: two method-reply queue handler mappings are built by the parser;
     * <br>postcondition: the corresponding method-reply handlers are available on the configuration.
     */
    public void testMethodReplyQueueMessageListenerParser() 
    {
        String schemaOpcode = "s";
        String configOpCode = "c";
        
        MethodReplyQueueMessageListenerParser parser = new MethodReplyQueueMessageListenerParser();
        
        parser.setCurrrentAttributeValue(schemaOpcode);
        parser.setCurrentAttributeName(Tag.OPCODE.toString());
        parser.setCurrrentAttributeValue(SchemaResponseMessageHandler.class.getName());
        parser.setCurrentAttributeName(Tag.CLASS_NAME.toString());
        parser.setCurrentAttributeName(Tag.HANDLER.toString());

        parser.setCurrrentAttributeValue(configOpCode);
        parser.setCurrentAttributeName(Tag.OPCODE.toString());
        parser.setCurrrentAttributeValue(ConfigurationMessageHandler.class.getName());
        parser.setCurrentAttributeName(Tag.CLASS_NAME.toString());
        parser.setCurrentAttributeName(Tag.HANDLER.toString());

        Map<Character,IMessageHandler> result = Configuration.getInstance().getMethodReplyQueueHandlers();
        
        assertEquals(2,result.size());
        
        assertEquals(SchemaResponseMessageHandler.class,result.get(schemaOpcode.charAt(0)).getClass());
        assertEquals(ConfigurationMessageHandler.class,result.get(configOpCode.charAt(0)).getClass());  
    }
    
    /**
     * Tests the execution of the type mapping parser.
     * 
     * <br>precondition: two type mappings are built by the parser;
     * <br>postcondition: the corresponding types are available on the configuration.
     */
    public void testMappingParser() throws NumberFormatException, UnknownTypeCodeException 
    {
        TypeMappingParser parser = new TypeMappingParser();
        
        String className = Uint8.class.getName();
        String validatorClassName = "a.b.c.Validator";
        
        parser.setCurrrentAttributeValue(String.valueOf(TestConstants.VALID_CODE));
        parser.setCurrentAttributeName(Tag.CODE.toString());
        parser.setCurrrentAttributeValue(className);
        parser.setCurrentAttributeName(Tag.CLASS_NAME.toString());
        parser.setCurrrentAttributeValue(validatorClassName);
        parser.setCurrentAttributeName(Tag.VALIDATOR_CLASS_NAME.toString());
        parser.setCurrentAttributeName(Tag.MAPPING.toString());
        
        Type type =Configuration.getInstance().getType(TestConstants.VALID_CODE);
        String vClassName = Configuration.getInstance().getValidatorClassName(type);
        
        assertEquals(Uint8.class, type.getClass());
        assertEquals(validatorClassName,vClassName);
    }
}