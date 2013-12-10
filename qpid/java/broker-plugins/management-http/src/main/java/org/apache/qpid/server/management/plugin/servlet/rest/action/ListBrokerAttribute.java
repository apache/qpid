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
package org.apache.qpid.server.management.plugin.servlet.rest.action;

import java.util.Map;

import org.apache.qpid.server.management.plugin.servlet.rest.Action;
import org.apache.qpid.server.model.Broker;

public class ListBrokerAttribute implements Action
{

    private final String _attributeName;
    private final String _name;

    public ListBrokerAttribute(String attributeName, String name)
    {
        _attributeName = attributeName;
        _name = name;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public Object perform(Map<String, Object> request, Broker broker)
    {
        return broker.getAttribute(_attributeName);
    }

}
