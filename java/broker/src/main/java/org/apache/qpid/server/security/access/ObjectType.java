/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.qpid.server.security.access;

import static org.apache.qpid.server.security.access.Operation.*;

import java.util.EnumSet;
import java.util.Set;

/**
 * An enumeration of all possible object types that can form part of an access control v2 rule.
 * 
 * Each object type is valid only for a certain set of {@link Operation}s, which are passed as a list to
 * the constructor, and can be checked using the {@link #isAllowed(Operation)} method.
 */
public enum ObjectType
{
    ALL(Operation.ALL),
    VIRTUALHOST(ACCESS),
    QUEUE(CREATE, DELETE, PURGE, CONSUME),
    TOPIC(CREATE, DELETE, PURGE, CONSUME),
    EXCHANGE(ACCESS, CREATE, DELETE, BIND, UNBIND, PUBLISH),
    LINK, // Not allowed in the Java broker
    ROUTE, // Not allowed in the Java broker
    METHOD(Operation.ALL, ACCESS, UPDATE, EXECUTE),
    OBJECT(ACCESS);
    
    private EnumSet<Operation> _actions;
    
    private ObjectType()
    {
        _actions = EnumSet.noneOf(Operation.class);
    }
    
    private ObjectType(Operation operation)
    {
        if (operation == Operation.ALL)
        {
            _actions = EnumSet.allOf(Operation.class);
        }
        else
        {
            _actions = EnumSet.of(operation);
        }
    }
    
    private ObjectType(Operation first, Operation...rest)
    {
        _actions = EnumSet.of(first, rest);
    }
    
    public Set<Operation> getActions()
    {
        return _actions;
    }
    
    public boolean isAllowed(Operation operation)
    {
        return _actions.contains(operation);
    }
    
    public static ObjectType parse(String text)
    {
        for (ObjectType object : values())
        {
            if (object.name().equalsIgnoreCase(text))
            {
                return object;
            }
        }
        throw new IllegalArgumentException("Not a valid object type: " + text);
    }
    
    public String toString()
    {
        String name = name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
