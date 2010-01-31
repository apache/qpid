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

import org.apache.qpid.server.exchange.ExchangeType;

import java.util.*;

public final class ExchangeConfigType extends ConfigObjectType<ExchangeConfigType, ExchangeConfig>
{
    private static final List<ExchangeProperty<?>> EXCHANGE_PROPERTIES = new ArrayList<ExchangeProperty<?>>();

    public static interface ExchangeProperty<S> extends ConfigProperty<ExchangeConfigType, ExchangeConfig, S>
    {
    }

    private abstract static class ExchangeReadWriteProperty<S>  extends ConfigProperty.ReadWriteConfigProperty<ExchangeConfigType, ExchangeConfig, S> implements ExchangeProperty<S>
    {
        public ExchangeReadWriteProperty(String name)
        {
            super(name);
            EXCHANGE_PROPERTIES.add(this);
        }
    }

    private abstract static class ExchangeReadOnlyProperty<S> extends ConfigProperty.ReadOnlyConfigProperty<ExchangeConfigType, ExchangeConfig, S> implements ExchangeProperty<S>
    {
        public ExchangeReadOnlyProperty(String name)
        {
            super(name);
            EXCHANGE_PROPERTIES.add(this);
        }
    }

    public static final ExchangeReadOnlyProperty<VirtualHostConfig> VIRTUAL_HOST_PROPERTY = new ExchangeReadOnlyProperty<VirtualHostConfig>("virtualHost")
    {
        public VirtualHostConfig getValue(ExchangeConfig object)
        {
            return object.getVirtualHost();
        }
    };

    public static final ExchangeReadOnlyProperty<String> NAME_PROPERTY = new ExchangeReadOnlyProperty<String>("name")
    {
        public String getValue(ExchangeConfig object)
        {
            return object.getName();
        }
    };

    public static final ExchangeReadOnlyProperty<Boolean> AUTODELETE_PROPERTY = new ExchangeReadOnlyProperty<Boolean>("autodelete")
    {
        public Boolean getValue(ExchangeConfig object)
        {
            return object.isAutoDelete();
        }
    };


    public static final ExchangeReadOnlyProperty<ExchangeConfig> ALTERNATE_EXCHANGE_PROPERTY = new ExchangeReadOnlyProperty<ExchangeConfig>("alternateExchange")
    {
        public ExchangeConfig getValue(ExchangeConfig object)
        {
            return object.getAlternateExchange();
        }
    };

    public static final ExchangeReadOnlyProperty<Map<String,Object>> ARGUMENTS = new ExchangeReadOnlyProperty<Map<String,Object>>("arguments")
    {
        public Map<String,Object> getValue(ExchangeConfig object)
        {
            return object.getArguments();
        }
    };

    private static final ExchangeConfigType INSTANCE = new ExchangeConfigType();

    private ExchangeConfigType()
    {
    }

    public Collection<ExchangeProperty<?>> getProperties()
    {
        return Collections.unmodifiableList(EXCHANGE_PROPERTIES);
    }

    public static ExchangeConfigType getInstance()
    {
        return INSTANCE;
    }



}