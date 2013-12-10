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
package org.apache.qpid.client.messaging.address;

import java.util.HashMap;

public class QpidExchangeOptions extends HashMap<String,Object> 
{	
    public static final String QPID_MSG_SEQUENCE = "qpid.msg_sequence";
    public static final String QPID_INITIAL_VALUE_EXCHANGE = "qpid.ive";
    public static final String QPID_EXCLUSIVE_BINDING = "qpid.exclusive-binding";
   
    public void setMessageSequencing()
    {
        this.put(QPID_MSG_SEQUENCE, 1);
    }
    
    public void setInitialValueExchange()
    {
        this.put(QPID_INITIAL_VALUE_EXCHANGE, 1);
    }
    
    public void setExclusiveBinding()
    {
        this.put(QPID_EXCLUSIVE_BINDING, 1);
    }
}
