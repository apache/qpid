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
package org.apache.qpid.messaging.address;

public final class AddressProperty 
{
    public static final String NODE = "node";
    public static final String LINK = "link";
    public static final String CREATE = "create";
    public static final String ASSERT = "assert";
    public static final String DELETE = "delete";
    public static final String FILTER = "filter";
    public static final String NO_LOCAL = "no-local";
    public static final String DURABLE = "durable";
    public static final String TYPE = "type";
    public static final String BROWSE = "browse";
    public static final String MODE = "mode";
    public static final String CAPACITY = "capacity";
    public static final String CAPACITY_SOURCE = "source";
    public static final String CAPACITY_TARGET = "target";
    public static final String NAME = "name";    
    public static final String RELIABILITY = "reliability";    
    
    public static String getFQN(String... propNames)
    {
        StringBuilder sb = new StringBuilder();
        for(String prop: propNames)
        {
            sb.append(prop).append("/");
        }
        return sb.substring(0, sb.length() -1);
    }
}
