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

package org.apache.qpid.qmf;

import org.apache.qpid.transport.codec.Encoder;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class QMFProperty
{
    private final Map<String, Object> _map = new LinkedHashMap<String, Object>();
    private static final String NAME = "name";
    private static final String TYPE = "type";
    private static final String ACCESS = "access";
    private static final String INDEX = "index";
    private static final String OPTIONAL = "optional";
    private static final String REF_PACKAGE = "refPackage";
    private static final String REF_CLASS = "refClass";
    private static final String UNIT = "unit";
    private static final String MIN = "min";
    private static final String MAX = "max";
    private static final String MAX_LENGTH = "maxlen";
    private static final String DESCRIPTION = "desc";


    public static enum AccessCode
    {
        RC,
        RW,
        RO;

        public int codeValue()
        {
            return ordinal()+1;
        }
    }

    public QMFProperty(String name, QMFType type, AccessCode accessCode, boolean index, boolean optional)
    {
        _map.put(NAME, name);
        _map.put(TYPE, type.codeValue());
        _map.put(ACCESS, accessCode.codeValue());
        _map.put(INDEX, index ? 1 : 0);
        _map.put(OPTIONAL, optional ? 1 :0);
    }


    public void setQMFClass(QMFClass qmfClass)
    {
 /*       _map.put(REF_CLASS, qmfClass.getName());
        _map.put(REF_PACKAGE, qmfClass.getPackage().getName());*/
    }

    public void setReferencedClass(String refClass)
    {
        _map.put(REF_CLASS, refClass);
    }

    public void setReferencedPackage(String refPackage)
    {
        _map.put(REF_CLASS, refPackage);
    }


    public String getName()
    {
        return (String) _map.get(NAME);
    }


    public void setUnit(String unit)
    {
        _map.put(UNIT, unit);
    }

    public void setMin(Number min)
    {
        _map.put(MIN, min);
    }

    public void setMax(Number max)
    {
        _map.put(MAX, max);
    }

    public void setMaxLength(int maxlen)
    {
        _map.put(MAX_LENGTH, maxlen);
    }


    public void setDescription(String description)
    {
        _map.put(DESCRIPTION, description);
    }

    public void encode(Encoder encoder)
    {
        encoder.writeMap(_map);
    }

}
