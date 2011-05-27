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
 *
 *
 */
package org.apache.qpid.server.security.access.config;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;

/**
 * An access control v2 rule action.
 * 
 * An action consists of an {@link Operation} on an {@link ObjectType} with certain properties, stored in a {@link Map}.
 * The operation and object should be an allowable combination, based on the {@link ObjectType#isAllowed(Operation)}
 * method of the object, which is exposed as the {@link #isAllowed()} method here. The internal {@link #propertiesMatch(Map)}
 * and {@link #valueMatches(String, String)} methods are used to determine wildcarded matching of properties, with
 * the empty string or "*" matching all values, and "*" at the end of a rule value indicating prefix matching.
 * <p>
 * The {@link #matches(Action)} method is intended to be used when determining precedence of rules, and
 * {@link #equals(Object)} and {@link #hashCode()} are intended for use in maps. This is due to the wildcard matching
 * described above.
 */
public class Action
{
    private Operation _operation;
    private ObjectType _object;
    private ObjectProperties _properties;
    
    public Action(Operation operation)
    {
        this(operation, ObjectType.ALL);
    }
    
    public Action(Operation operation, ObjectType object, String name)
    {
        this(operation, object, new ObjectProperties(name));
    }
    
    public Action(Operation operation, ObjectType object)
    {
        this(operation, object, ObjectProperties.EMPTY);
    }
    
    public Action(Operation operation, ObjectType object, ObjectProperties properties)
    {
        setOperation(operation);
        setObjectType(object);
        setProperties(properties);
    }
    
    public Operation getOperation()
    {
        return _operation;
    }

    public void setOperation(Operation operation)
    {
        _operation = operation;
    }

    public ObjectType getObjectType()
    {
        return _object;
    }

    public void setObjectType(ObjectType object)
    {
        _object = object;
    }

    public ObjectProperties getProperties()
    {
        return _properties;
    }
    
    public void setProperties(ObjectProperties properties)
    {
        _properties = properties;
    }
    
    public boolean isAllowed()
    {
        return _object.isAllowed(_operation);
    }

    /** @see Comparable#compareTo(Object) */
    public boolean matches(Action a)
    {
        return (Operation.ALL == a.getOperation()
                || (getOperation() == a.getOperation()
                    && getObjectType() == a.getObjectType()
                    && _properties.matches(a.getProperties())));
    }

    /**
     * An ordering based on specificity
     * 
     * @see Comparator#compare(Object, Object)
     */
    public class Specificity implements Comparator<Action>
    {
        public int compare(Action a, Action b)
        {
            if (a.getOperation() == Operation.ALL && b.getOperation() != Operation.ALL)
            {
                return 1; // B is more specific
            }
            else if (b.getOperation() == Operation.ALL && a.getOperation() != Operation.ALL)
            {
                return 1; // A is more specific
            }
            else if (a.getOperation() == b.getOperation())
            {
                // Same operator, compare rest of action
                
//                    || (getOperation() == a.getOperation()
//                        && getObjectType() == a.getObjectType()
//                        && _properties.matches(a.getProperties())));

                return 1; // b is more specific
            }
            else // Different operations
            {
                return a.getOperation().compareTo(b.getOperation()); // Arbitrary
            }
        }
    }

    /** @see Object#equals(Object) */
    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Action))
        {
            return false;
        }
        Action a = (Action) o;
        
        return new EqualsBuilder()
                .append(_operation, a.getOperation())
                .append(_object, a.getObjectType())
                .appendSuper(_properties.equals(a.getProperties()))
                .isEquals();
    }

    /** @see Object#hashCode() */
    @Override
    public int hashCode()
    {
        return new HashCodeBuilder()
                .append(_operation)
                .append(_operation)
                .append(_properties)
                .toHashCode();
    }

    /** @see Object#toString() */
    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("operation", _operation)
                .append("objectType", _object)
                .append("properties", _properties)
                .toString();
    }
}
