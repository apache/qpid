/*
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
 */
package org.apache.qpid.disttest.controller.config;

import java.util.Map;

import org.apache.qpid.disttest.client.property.PropertyValue;
import org.apache.qpid.disttest.message.CreateMessageProviderCommand;

public class MessageProviderConfig
{
    private String _name;
    private Map<String, PropertyValue> _messageProperties;

    public MessageProviderConfig()
    {
        super();
    }

    public MessageProviderConfig(String name, Map<String, PropertyValue> messageProperties)
    {
        super();
        _name = name;
        _messageProperties = messageProperties;
    }

    public String getName()
    {
        return _name;
    }

    public Map<String, PropertyValue> getMessageProperties()
    {
        return _messageProperties;
    }

    public CreateMessageProviderCommand createCommand()
    {
        CreateMessageProviderCommand command = new CreateMessageProviderCommand();
        command.setProviderName(_name);
        command.setMessageProperties(_messageProperties);
        return command;
    }
}
