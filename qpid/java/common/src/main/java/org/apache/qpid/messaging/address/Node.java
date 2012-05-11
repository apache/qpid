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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Node
{

    private final String _name;

    private final boolean _durable;
    private final NodeType _type;

    private final AddressPolicy _createPolicy;
    private final AddressPolicy _assertPolicy;
    private final AddressPolicy _deletePolicy;

    private final Map<String, Object> _xDeclareProps;
    private final List<Object> _xBindingProps;

    public Node(String name, 
                NodeType type, 
                boolean durable, 
                AddressPolicy createPolicy, 
                AddressPolicy assertPolicy, 
                AddressPolicy deletePolicy, 
                Map<String, Object> xDeclareProps, 
                List<Object> xBindingProps)
    {
        _name = name;
        _durable = durable;
        _type = type == null ? NodeType.QUEUE : type;
        _createPolicy = createPolicy == null ? AddressPolicy.NEVER : createPolicy;
        _assertPolicy = assertPolicy == null ? AddressPolicy.NEVER : assertPolicy;
        _deletePolicy = deletePolicy == null ? AddressPolicy.NEVER : deletePolicy;
        _xDeclareProps = xDeclareProps == null 
                ? Collections.EMPTY_MAP
                : Collections.unmodifiableMap(new HashMap<String, Object>(xDeclareProps));
        _xBindingProps = xBindingProps == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<Object>(xBindingProps));
    }

    public String getName()
    {
        return _name;
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

}