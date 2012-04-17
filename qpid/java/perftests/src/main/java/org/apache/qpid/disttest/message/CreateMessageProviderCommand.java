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
package org.apache.qpid.disttest.message;

import java.util.Map;

import org.apache.qpid.disttest.client.property.PropertyValue;

public class CreateMessageProviderCommand extends Command
{
    private String _providerName;
    private Map<String, PropertyValue> _messageProperties;

    public CreateMessageProviderCommand()
    {
        super(CommandType.CREATE_MESSAGE_PROVIDER);
    }

    public String getProviderName()
    {
        return _providerName;
    }

    public void setProviderName(String providerName)
    {
        this._providerName = providerName;
    }

    public Map<String, PropertyValue> getMessageProperties()
    {
        return _messageProperties;
    }

    public void setMessageProperties(Map<String, PropertyValue> messageProperties)
    {
        this._messageProperties = messageProperties;
    }
}
