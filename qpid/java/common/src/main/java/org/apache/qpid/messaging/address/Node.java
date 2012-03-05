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
        QUEUE, TOPIC, UNRESOLVED;

        public static NodeType getNodeType(String type) throws AddressException
        {
            if (type == null)
            {
                return UNRESOLVED;
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

    protected String name;

    protected boolean durable = false;
    protected NodeType type = NodeType.UNRESOLVED;

    protected AddressPolicy createPolicy = AddressPolicy.NEVER;
    protected AddressPolicy assertPolicy = AddressPolicy.NEVER;
    protected AddressPolicy deletePolicy = AddressPolicy.NEVER;

    protected Map<String, Object> xDeclareProps = (Map<String, Object>) Collections.EMPTY_MAP;
    protected Map<String, Object> xBindingProps = (Map<String, Object>) Collections.EMPTY_MAP;

    public String getName()
    {
        return name;
    }

    public boolean isDurable()
    {
        return durable;
    }

    public NodeType getType()
    {
        return type;
    }

    public AddressPolicy getCreatePolicy()
    {
        return createPolicy;
    }

    public AddressPolicy getAssertPolicy()
    {
        return assertPolicy;
    }

    public AddressPolicy getDeletePolicy()
    {
        return deletePolicy;
    }

    public Map<String, Object> getXDeclareProperties()
    {
        return xDeclareProps;
    }

    public Map<String, Object> getXBindingProperties()
    {
        return xBindingProps;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setDurable(boolean durable)
    {
        this.durable = durable;
    }

    public void setType(NodeType type)
    {
        this.type = type;
    }

    public void setCreatePolicy(AddressPolicy createPolicy)
    {
        this.createPolicy = createPolicy;
    }

    public void setAssertPolicy(AddressPolicy assertPolicy)
    {
        this.assertPolicy = assertPolicy;
    }

    public void setDeletePolicy(AddressPolicy deletePolicy)
    {
        this.deletePolicy = deletePolicy;
    }

    public void setxDeclareProps(Map<String, Object> xDeclareProps)
    {
        this.xDeclareProps = xDeclareProps;
    }

    public void setxBindingProps(Map<String, Object> xBindingProps)
    {
        this.xBindingProps = xBindingProps;
    }
}