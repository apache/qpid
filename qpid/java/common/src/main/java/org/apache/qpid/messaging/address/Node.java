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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Node
{
    public enum AddressPolicy
    {
        NEVER, SENDER, RECEIVER, ALWAYS;

        public static AddressPolicy getAddressPolicy(String policy)
        throws AddressException
        {
            try
            {
                return policy == null ? NEVER : Enum.valueOf(AddressPolicy.class, policy.toUpperCase());
            }
            catch (IllegalArgumentException e)
            {
                throw new AddressException ((new StringBuffer("Invalid address policy")
                .append(" '").append(policy).append("' ")
                .append("Valid policy types are { NEVER, ALWAYS, SENDER, RECEIVER}.")).toString());
            }
        }
    };

    public enum NodeType
    {
        QUEUE, TOPIC;

        public static NodeType getNodeType(String type) throws AddressException
        {
            try
            {
                return type == null ? QUEUE : Enum.valueOf(NodeType.class, type.toUpperCase());
            }
            catch (IllegalArgumentException e)
            {
                throw new AddressException ((new StringBuffer("Invalid node type")
                .append(" '").append(type).append("' ")
                .append("Valid node types are { QUEUE, TOPIC }.")).toString());
            }
        }
    };

    private String name;

    private boolean _durable = false;
    private NodeType _type = NodeType.QUEUE;

    private AddressPolicy _createPolicy = AddressPolicy.NEVER;
    private AddressPolicy _assertPolicy = AddressPolicy.NEVER;
    private AddressPolicy _deletePolicy = AddressPolicy.NEVER;

    private Map<String, Object> _xDeclareProps = Collections.emptyMap();
    private List<Object> _xBindingProps = Collections.emptyList();
    private AtomicBoolean readOnly = new AtomicBoolean(false);

    public String getName()
    {
        return name;
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public NodeType getType()
    {
        return _type;
    }

    public AddressPolicy getCreatePolicy()
    {
        return _createPolicy;
    }

    public AddressPolicy getAssertPolicy()
    {
        return _assertPolicy;
    }

    public AddressPolicy getDeletePolicy()
    {
        return _deletePolicy;
    }

    public Map<String, Object> getDeclareProperties()
    {
        return Collections.unmodifiableMap(_xDeclareProps);
    }

    public List<Object> getBindingProperties()
    {
        return Collections.unmodifiableList(_xBindingProps);
    }

    public void setName(String name)
    {
        checkReadOnly();
        this.name = name;
    }

    public void setDurable(boolean durable)
    {
        checkReadOnly();
        this._durable = durable;
    }

    public void setType(NodeType type)
    {
        checkReadOnly();
        this._type = type;
    }

    public void setCreatePolicy(AddressPolicy createPolicy)
    {
        checkReadOnly();
        this._createPolicy = createPolicy;
    }

    public void setAssertPolicy(AddressPolicy assertPolicy)
    {
        checkReadOnly();
        this._assertPolicy = assertPolicy;
    }

    public void setDeletePolicy(AddressPolicy deletePolicy)
    {
        checkReadOnly();
        this._deletePolicy = deletePolicy;
    }

    public void setDeclareProps(Map<String, Object> xDeclareProps)
    {
        checkReadOnly();
        this._xDeclareProps = xDeclareProps;
    }

    public void setBindingProps(List<Object> xBindingProps)
    {
        checkReadOnly();
        this._xBindingProps = xBindingProps;
    }

    public void checkReadOnly()
    {
        if (readOnly.get())
        {
            throw new IllegalArgumentException("Once initialized the Link object is immutable");
        }
    }

    public void markReadOnly()
    {
        readOnly.set(true);
    }
}