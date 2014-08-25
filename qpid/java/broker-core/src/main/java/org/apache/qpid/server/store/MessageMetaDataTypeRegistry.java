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
package org.apache.qpid.server.store;

import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class MessageMetaDataTypeRegistry
{
    private static MessageMetaDataType[] values;

    static
    {
        int maxOrdinal = -1;

        Iterable<MessageMetaDataType> messageMetaDataTypes =
                new QpidServiceLoader().atLeastOneInstanceOf(MessageMetaDataType.class);

        for(MessageMetaDataType type : messageMetaDataTypes)
        {
            if(type.ordinal()>maxOrdinal)
            {
                maxOrdinal = type.ordinal();
            }
        }
        values = new MessageMetaDataType[maxOrdinal+1];
        for(MessageMetaDataType type : new QpidServiceLoader().instancesOf(MessageMetaDataType.class))
        {
            if(values[type.ordinal()] != null)
            {
                throw new IllegalStateException("Multiple MessageDataType ("
                                                 +values[type.ordinal()].getClass().getName()
                                                 +", "
                                                 + type.getClass().getName()
                                                 + ") defined for the same ordinal value: " + type.ordinal());
            }
            values[type.ordinal()] = type;
        }
    }


    public static MessageMetaDataType fromOrdinal(int ordinal)
    {
        return values[ordinal];
    }

}
