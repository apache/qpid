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
package org.apache.qpid.management;

import javax.management.ObjectName;
import javax.xml.namespace.QName;

/**
 * Enumeration of literal strings to avoid code duplication.
 */
public abstract class Names
{
    /** Name of the qpid management exchange. */
    public static String MANAGEMENT_EXCHANGE = "qpid.management";    
    public static String MANAGEMENT_ROUTING_KEY = "console.#";
   
    public static String MANAGEMENT_QUEUE_PREFIX = "management.";
    public static String METHOD_REPLY_QUEUE_PREFIX = "reply.";
   
    public static String AMQ_DIRECT_QUEUE = "amq.direct";
    public static String AGENT_ROUTING_KEY_PREFIX = "agent.";
    public static String AGENT_ROUTING_KEY = AGENT_ROUTING_KEY_PREFIX+"1.0";

    public static String APPLICATION_NAME ="Q-Man";
    
    // Attributes
    public static String PACKAGE = "package";
    public static String CLASS = "class";
    public static String EVENT = "event";
    public static String OBJECT_ID="objectId";    
    public static String BROKER_ID = "brokerID";
    public static String DOMAIN_NAME = "Q-MAN";
        
    public static String ARG_COUNT_PARAM_NAME = "argCount";
    public static String DEFAULT_PARAM_NAME ="default";
    
    public static String NUMBER_VALIDATOR = "org.apache.qpid.management.domain.model.QpidProperty$NumberValidator";
    public static String STRING_VALIDATOR = "org.apache.qpid.management.domain.model.QpidProperty$StringValidator";
    
    public static String QMAN_CONFIG_OPTION_NAME = "qman-config";
    
    public static String ADD_BROKER_OPERATION_NAME = "addBroker";
    
    public static String NOT_AVAILABLE = "N.A.";
    
    public static ObjectName QPID_EMULATOR_OBJECT_NAME;
    static 
    {
	    try 
	    {
	    	QPID_EMULATOR_OBJECT_NAME = new ObjectName(
	    			new StringBuilder()
	    				.append(DOMAIN_NAME)
	    				.append(':')
	    				.append("Name=Qpid,Type=Emulator")
	    				.toString());
	    } catch(Exception exception)
	    {
	    	throw new ExceptionInInitializerError(exception);
	    }
    }    
    
    public static ObjectName QMAN_OBJECT_NAME;
    static 
    {
	    try 
	    {
	    	QMAN_OBJECT_NAME = new ObjectName(
	    			new StringBuilder()
	    				.append(DOMAIN_NAME)
	    				.append(':')
	    				.append("Type=Service")
	    				.toString());
	    } catch(Exception exception)
	    {
	    	throw new ExceptionInInitializerError(exception);
	    }
    }
    
    // WSDM Stuff
    public static String NAMESPACE_URI = "http://amqp.apache.org/qpid/management/qman";
    public static String ADDRESSING_URI = "http://amqp.apache.org/qpid/management/qman/addressing";
    public static String ADDRESSING_PREFIX = "qman-wsa";
    
    public static final QName RESOURCE_ID_QNAME = new QName(ADDRESSING_URI, "ResourceId", ADDRESSING_PREFIX);   
	public final static String PREFIX = "qman";
	public final static String QMAN_RESOURCE_NAME = "QManWsResource";
	
    public final static String VALIDATE_WSRP_PARAM = "validate-wsrp-schema";

    public static final String WEB_APP_CLASSES_FOLDER = "/WEB-INF/classes";
    public static final String DEFAULT_ENDPOINT_URI = "http://localhost:8080/qman/adapter";
    
	public final static QName  QMAN_RESOURCE_PORT_TYPE_NAME = new QName(
			Names.NAMESPACE_URI,
			"QManWsResourcePortType",
			Names.PREFIX);

	public final static String NAME_ATTRIBUTE = "name";
	public final static String MODIFIABILITY = "modifiability";
	public final static String READ_WRITE = "read-write";
	public final static String READ_ONLY = "read-only";
	public final static String MUTABILITY = "mutability";
	public final static String MUTABLE = "mutable";
	
	public final static String ENTRY = "entry";
	public final static String KEY = "key";
	public final static String VALUE = "value";
	public final static String TYPE = "type";
	public final static String XSI_TYPE = "xsi:"+TYPE;
	
	public final static String ADAPTER_PORT= "qman.port";	
}