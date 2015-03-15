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
package org.apache.qpid.server.filter;

import org.apache.qpid.filter.FilterableMessage;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.ServerMessage;

public interface Filterable extends FilterableMessage
{
    AMQMessageHeader getMessageHeader();

    boolean isPersistent();

    boolean isRedelivered();

    Object getConnectionReference();

    long getMessageNumber();

    long getArrivalTime();

    public class Factory
    {

        public static Filterable newInstance(final ServerMessage message, final InstanceProperties properties)
        {
            return new Filterable()
            {

                @Override
                public AMQMessageHeader getMessageHeader()
                {
                    return message.getMessageHeader();
                }

                @Override
                public boolean isPersistent()
                {
                    return Boolean.TRUE.equals(properties.getProperty(InstanceProperties.Property.PERSISTENT));
                }

                @Override
                public boolean isRedelivered()
                {
                    return Boolean.TRUE.equals(properties.getProperty(InstanceProperties.Property.REDELIVERED));
                }

                @Override
                public Object getConnectionReference()
                {
                    return message.getConnectionReference();
                }

                @Override
                public long getMessageNumber()
                {
                    return message.getMessageNumber();
                }

                @Override
                public long getArrivalTime()
                {
                    return message.getArrivalTime();
                }

                @Override
                public String getReplyTo()
                {
                    return message.getMessageHeader().getReplyTo();
                }

                @Override
                public String getType()
                {
                    return message.getMessageHeader().getType();
                }

                @Override
                public byte getPriority()
                {
                    return message.getMessageHeader().getPriority();
                }

                @Override
                public String getMessageId()
                {
                    return message.getMessageHeader().getMessageId();
                }

                @Override
                public long getTimestamp()
                {
                    return message.getMessageHeader().getTimestamp();
                }

                @Override
                public String getCorrelationId()
                {
                    return message.getMessageHeader().getCorrelationId();
                }

                @Override
                public long getExpiration()
                {
                    return message.getMessageHeader().getExpiration();
                }

                @Override
                public Object getHeader(String name)
                {
                    return message.getMessageHeader().getHeader(name);
                }

            };
        }
    }
}
