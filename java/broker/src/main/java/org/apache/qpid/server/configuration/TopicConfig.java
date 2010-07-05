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
package org.apache.qpid.server.configuration;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;

public class TopicConfig extends ConfigurationPlugin
{
    public TopicConfig()
    {
        _configuration = new PropertiesConfiguration();
    }

    @Override
    public String[] getElementsProcessed()
    {
        return new String[]{"name", "subscriptionName"};
    }

    public String getName()
    {
        // If we don't have a specific topic then this config is for all topics.
        return getStringValue("name", "#");
    }

    public String getSubscriptionName()
    {
        return getStringValue("subscriptionName");
    }

    public void validateConfiguration() throws ConfigurationException
    {
        if (_configuration.isEmpty())
        {
            throw new ConfigurationException("Topic section cannot be empty.");
        }

        if (getStringValue("name") == null && getSubscriptionName() == null)
        {
            throw new ConfigurationException("Topic section must have a 'name' or 'subscriptionName' element.");
        }

        System.err.println("********* Created TC:"+this);
    }


    @Override
    public String formatToString()
    {
        String response = "Topic:"+getName();
        if (getSubscriptionName() != null)
        {
               response += ", SubscriptionName:"+getSubscriptionName();
        }

        return response;
    }    
}