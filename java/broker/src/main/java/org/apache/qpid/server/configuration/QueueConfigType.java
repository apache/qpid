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

public final class QueueConfigType extends ConfigObjectType<QueueConfigType, QueueConfig>
{
    private static final List<QueueProperty<?>> QUEUE_PROPERTIES = new ArrayList<QueueProperty<?>>();

    public static interface QueueProperty<S> extends ConfigProperty<QueueConfigType, QueueConfig, S>
    {
    }

    private abstract static class QueueReadWriteProperty<S>  extends ConfigProperty.ReadWriteConfigProperty<QueueConfigType, QueueConfig, S> implements QueueProperty<S>
    {
        public QueueReadWriteProperty(String name)
        {
            super(name);
            QUEUE_PROPERTIES.add(this);
        }
    }

    private abstract static class QueueReadOnlyProperty<S> extends ConfigProperty.ReadOnlyConfigProperty<QueueConfigType, QueueConfig, S> implements QueueProperty<S>
    {
        public QueueReadOnlyProperty(String name)
        {
            super(name);
            QUEUE_PROPERTIES.add(this);
        }
    }

    public static final QueueReadOnlyProperty<VirtualHostConfig> VISTUAL_HOST_PROPERTY = new QueueReadOnlyProperty<VirtualHostConfig>("virtualHost")
    {
        public VirtualHostConfig getValue(QueueConfig object)
        {
            return object.getVirtualHost();
        }
    };

    public static final QueueReadOnlyProperty<String> NAME_PROPERTY = new QueueReadOnlyProperty<String>("name")
    {
        public String getValue(QueueConfig object)
        {
            return object.getName();
        }
    };

    public static final QueueReadOnlyProperty<Boolean> AUTODELETE_PROPERTY = new QueueReadOnlyProperty<Boolean>("autodelete")
    {
        public Boolean getValue(QueueConfig object)
        {
            return object.isAutoDelete();
        }
    };

    public static final QueueReadOnlyProperty<Boolean> EXCLUSIVE_PROPERTY = new QueueReadOnlyProperty<Boolean>("exclusive")
    {
        public Boolean getValue(QueueConfig object)
        {
            return object.isExclusive();
        }
    };

    public static final QueueReadOnlyProperty<ExchangeConfig> ALTERNATE_EXCHANGE_PROPERTY = new QueueReadOnlyProperty<ExchangeConfig>("alternateExchange")
    {
        public ExchangeConfig getValue(QueueConfig object)
        {
            return object.getAlternateExchange();
        }
    };

    public static final QueueReadOnlyProperty<Map<String,Object>> ARGUMENTS = new QueueReadOnlyProperty<Map<String,Object>>("arguments")
    {
        public Map<String,Object> getValue(QueueConfig object)
        {
            return object.getArguments();
        }
    };


    private static final QueueConfigType INSTANCE = new QueueConfigType();

    private QueueConfigType()
    {
    }

    public Collection<QueueProperty<?>> getProperties()
    {
        return Collections.unmodifiableList(QUEUE_PROPERTIES);
    }

    public static QueueConfigType getInstance()
    {
        return INSTANCE;
    }



}