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
package org.apache.qpid.nclient.amqp.state;

/**
 * The Type of States used in the AMQ protocol.
 * This allows to partition listeners by the type of states they want 
 * to listen rather than all.
 * For example an Object might only be interested in Channel state
 */
public class AMQPStateType
{
    private final int _typeId;

    private final String _typeName;

    private AMQPStateType(int id, String name)
    {
        _typeId = id;
        _typeName = name;
    }

    public String toString()
    {
        return "AMQState: id = " + _typeId + " name: " + _typeName;
    }

    // Connection state
    public static final AMQPStateType CONNECTION_STATE = new AMQPStateType(0, "CONNECTION_STATE");    
    public static final AMQPStateType CHANNEL_STATE = new AMQPStateType(1, "CHANNEL_STATE");
    
}
