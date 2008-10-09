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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.management.domain.model.DomainModel;
import org.apache.qpid.management.domain.model.type.Binary;

public interface TestConstants
{
    String QPID_PACKAGE_NAME = "qpid";
    String EXCHANGE_CLASS_NAME = "exchange";
    Binary HASH = new Binary(new byte []{1,2,3,4,5,6,7,8,9});
    int VALID_CODE = 1;
    
    
    UUID BROKER_ID = UUID.randomUUID();
    Binary OBJECT_ID = new Binary(new byte []{1,2,3,2,1,1,2,3});

    DomainModel DOMAIN_MODEL = new DomainModel(BROKER_ID);
    
    List<Map<String, Object>> EMPTY_PROPERTIES_SCHEMA = new LinkedList<Map<String,Object>>();
    List<Map<String, Object>> EMPTY_STATISTICS_SCHEMA = new LinkedList<Map<String,Object>>();
    List<Map<String, Object>> EMPTY_METHODS_SCHEMA = new LinkedList<Map<String,Object>>();
    List<Map<String, Object>> EMPTY_EVENTS_SCHEMA = new LinkedList<Map<String,Object>>();
}