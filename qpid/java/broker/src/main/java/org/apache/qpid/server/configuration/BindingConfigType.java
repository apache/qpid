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

public final class BindingConfigType extends ConfigObjectType<BindingConfigType, BindingConfig>
{
    private static final List<BindingProperty<?>> BINDING_PROPERTIES = new ArrayList<BindingProperty<?>>();

    public static interface BindingProperty<S> extends ConfigProperty<BindingConfigType, BindingConfig, S>
    {
    }

    private abstract static class BindingReadWriteProperty<S>  extends ConfigProperty.ReadWriteConfigProperty<BindingConfigType, BindingConfig, S> implements BindingProperty<S>
    {
        public BindingReadWriteProperty(String name)
        {
            super(name);
            BINDING_PROPERTIES.add(this);
        }
    }

    private abstract static class BindingReadOnlyProperty<S> extends ConfigProperty.ReadOnlyConfigProperty<BindingConfigType, BindingConfig, S> implements BindingProperty<S>
    {
        public BindingReadOnlyProperty(String name)
        {
            super(name);
            BINDING_PROPERTIES.add(this);
        }
    }

    public static final BindingReadOnlyProperty<ExchangeConfig> EXCHANGE_PROPERTY = new BindingReadOnlyProperty<ExchangeConfig>("exchange")
    {
        public ExchangeConfig getValue(BindingConfig object)
        {
            return object.getExchange();
        }
    };

    public static final BindingReadOnlyProperty<QueueConfig> QUEUE_PROPERTY = new BindingReadOnlyProperty<QueueConfig>("queue")
    {
        public QueueConfig getValue(BindingConfig object)
        {
            return object.getQueue();
        }
    };

    public static final BindingReadOnlyProperty<String> BINDING_KEY_PROPERTY = new BindingReadOnlyProperty<String>("bindingKey")
    {
        public String getValue(BindingConfig object)
        {
            return object.getBindingKey();
        }
    };

    public static final BindingReadOnlyProperty<Map<String,Object>> ARGUMENTS = new BindingReadOnlyProperty<Map<String,Object>>("arguments")
    {
        public Map<String,Object> getValue(BindingConfig object)
        {
            return object.getArguments();
        }
    };

    public static final BindingReadOnlyProperty<String> ORIGIN_PROPERTY = new BindingReadOnlyProperty<String>("origin")
    {
        public String getValue(BindingConfig object)
        {
            return object.getOrigin();
        }
    };

    private static final BindingConfigType INSTANCE = new BindingConfigType();

    private BindingConfigType()
    {
    }

    public Collection<BindingProperty<?>> getProperties()
    {
        return Collections.unmodifiableList(BINDING_PROPERTIES);
    }

   public static BindingConfigType getInstance()
    {
        return INSTANCE;
    }



}