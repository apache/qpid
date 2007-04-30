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
 * States used in the AMQ protocol. Used by the finite state machine to determine
 * valid responses.
 */
public class AMQPState
{
    private final int _id;

    private final String _name;

    private AMQPState(int id, String name)
    {
        _id = id;
        _name = name;
    }

    public String toString()
    {
        //return "AMQState: id = " + _id + " name: " + _name;
    	return _name; // looks better with loggin
    }

    // Connection state
    public static final AMQPState CONNECTION_UNDEFINED = new AMQPState(0, "CONNECTION_UNDEFINED");    
    public static final AMQPState CONNECTION_NOT_STARTED = new AMQPState(1, "CONNECTION_NOT_STARTED");    
    public static final AMQPState CONNECTION_NOT_SECURE = new AMQPState(2, "CONNECTION_NOT_SECURE");    
    public static final AMQPState CONNECTION_NOT_TUNED = new AMQPState(2, "CONNECTION_NOT_TUNED");    
    public static final AMQPState CONNECTION_NOT_OPENED = new AMQPState(3, "CONNECTION_NOT_OPENED");
    public static final AMQPState CONNECTION_OPEN = new AMQPState(4, "CONNECTION_OPEN");
    public static final AMQPState CONNECTION_CLOSING = new AMQPState(5, "CONNECTION_CLOSING");    
    public static final AMQPState CONNECTION_CLOSED = new AMQPState(6, "CONNECTION_CLOSED");
    
    // Channel state
    public static final AMQPState CHANNEL_NOT_OPENED = new AMQPState(10, "CHANNEL_NOT_OPENED");    
    public static final AMQPState CHANNEL_OPENED = new AMQPState(11, "CHANNEL_OPENED");    
    public static final AMQPState CHANNEL_CLOSED = new AMQPState(11, "CHANNEL_CLOSED");
    public static final AMQPState CHANNEL_SUSPEND = new AMQPState(11, "CHANNEL_SUSPEND");
    
    // Distributed Transaction state
    public static final AMQPState DTX_CHANNEL_NOT_SELECTED = new AMQPState(10, "DTX_CHANNEL_NOT_SELECTED");
    public static final AMQPState DTX_NOT_STARTED = new AMQPState(10, "DTX_NOT_STARTED");
    public static final AMQPState DTX_STARTED = new AMQPState(10, "DTX_STARTED");
    public static final AMQPState DTX_END = new AMQPState(10, "DTX_END");
}
