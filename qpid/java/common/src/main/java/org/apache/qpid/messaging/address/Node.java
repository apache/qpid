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

import org.apache.qpid.messaging.address.Link.Reliability;

public class Node
{
    public enum AddressPolicy
    {
        NEVER, SENDER, RECEIVER, ALWAYS;

        public static AddressPolicy getAddressPolicy(String policy)
        throws AddressException
        {
            if (policy == null || policy.equalsIgnoreCase("never"))
            {
                return NEVER;
            }
            else if (policy.equalsIgnoreCase("always"))
            {
                return ALWAYS;
            }
            else if (policy.equalsIgnoreCase("sender"))
            {
                return SENDER;
            }
            else if (policy.equalsIgnoreCase("receiver"))
            {
                return RECEIVER;
            }
            else
            {
                throw new AddressException("Invalid address policy type : '"
                        + policy + "'");
            }
        }
    };

    public enum NodeType
    {
        QUEUE, TOPIC;

        public static NodeType getNodeType(String type) throws AddressException
        {
            if (type == null)
            {
                return QUEUE; // defaults to queue
            }
            else if (type.equalsIgnoreCase("queue"))
            {
                return QUEUE;
            }
            else if (type.equalsIgnoreCase("topic"))
            {
                return TOPIC;
            }else    
            {
                throw new AddressException("Invalid node type : '" + type + "'");
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
        return _xDeclareProps;
    }

    public List<Object> getBindingProperties()
    {
        return _xBindingProps;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setDurable(boolean durable)
    {
        this._durable = durable;
    }

    public void setType(NodeType type)
    {
        this._type = type;
    }

    public void setCreatePolicy(AddressPolicy createPolicy)
    {
        this._createPolicy = createPolicy;
    }

    public void setAssertPolicy(AddressPolicy assertPolicy)
    {
        this._assertPolicy = assertPolicy;
    }

    public void setDeletePolicy(AddressPolicy deletePolicy)
    {
        this._deletePolicy = deletePolicy;
    }

    public void setDeclareProps(Map<String, Object> xDeclareProps)
    {
        this._xDeclareProps = xDeclareProps;
    }

    public void setBindingProps(List<Object> xBindingProps)
    {
        this._xBindingProps = xBindingProps;
    }
}