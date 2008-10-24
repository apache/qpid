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

/**
 * Enumeration of literal strings to avoid code duplication.
 * 
 * @author Andrea Gazzarini
 */
public interface Names
{
    /** Name of the qpid management exchange. */
    String MANAGEMENT_EXCHANGE = "qpid.management";    
    String MANAGEMENT_ROUTING_KEY = "console.#";
   
    String MANAGEMENT_QUEUE_PREFIX = "management.";
    String METHOD_REPLY_QUEUE_PREFIX = "reply.";
   
    String AMQ_DIRECT_QUEUE = "amq.direct";
    String AGENT_ROUTING_KEY = "agent.1.0";
   
    String BROKER_ROUTING_KEY = "broker";
    
    // Attributes
    String PACKAGE = "package";
    String CLASS = "class";
    String OBJECT_ID="objectID";    
    String BROKER_ID = "brokerID";
    String DOMAIN_NAME = "Q-MAN";
    
    String CONFIGURATION_FILE_NAME = "/org/apache/qpid/management/config.xml";
    
    String ARG_COUNT_PARAM_NAME = "argCount";
    String DEFAULT_PARAM_NAME ="default";
}
