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
package org.apache.qpid.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.qpid.framing.AMQShortString;

public enum CustomJMSXProperty
{
    JMS_AMQP_NULL,
    JMS_QPID_DESTTYPE,
    JMSXGroupID,
    JMSXGroupSeq,
    JMSXUserID;

    private static List<String> _names;

    static
    {
        CustomJMSXProperty[] properties = values();
        _names = new ArrayList<String>(properties.length);
        for(CustomJMSXProperty property :  properties)
        {
            _names.add(property.toString());
        }

    }

    private final AMQShortString _nameAsShortString;

    CustomJMSXProperty()
    {
        _nameAsShortString = new AMQShortString(toString());
    }

    public AMQShortString getShortStringName()
    {
        return _nameAsShortString;
    }

    public static Enumeration asEnumeration()
    {
        return Collections.enumeration(_names);
    }
}
