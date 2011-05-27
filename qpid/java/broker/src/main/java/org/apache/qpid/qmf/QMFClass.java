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

import org.apache.qpid.server.configuration.ConfiguredObject;

import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;

abstract public class QMFClass
{


    public enum Type
    {
        OBJECT((byte)1),
        EVENT((byte)2);

        private final byte _value;

        Type(byte value)
        {
            _value = value;
        }

        public byte getValue()
        {
            return _value;
        }
    }

    private final Type _type;
    private QMFPackage _package;
    private final String _name;
    private byte[] _schemaHash;

    private Map<String, QMFProperty> _properties = new LinkedHashMap<String, QMFProperty>();
    private Map<String, QMFStatistic> _statistics = new LinkedHashMap<String, QMFStatistic>();
    private Map<String, QMFMethod> _methods = new LinkedHashMap<String, QMFMethod>();



    public QMFClass(Type type, String name, byte[] schemaHash, List<QMFProperty> properties,
                    List<QMFStatistic> statistics, List<QMFMethod> methods)
    {
        this(type, name, schemaHash);
        setProperties(properties);
        setStatistics(statistics);
        setMethods(methods);
    }


    public QMFClass(Type type, String name, byte[] schemaHash)

    {
        _type = type;
        _name = name;
        _schemaHash = schemaHash;

    }

    protected void setProperties(List<QMFProperty> properties)
    {
        for(QMFProperty prop : properties)
        {
            _properties.put(prop.getName(), prop);
        }
    }

    protected void setStatistics(List<QMFStatistic> statistics)
    {
        for(QMFStatistic stat : statistics)
        {
            _statistics.put(stat.getName(), stat);
        }
    }


    protected void setMethods(List<QMFMethod> methods)
    {
        for(QMFMethod method : methods)
        {
            _methods.put(method.getName(), method);
        }
    }

    public void setPackage(QMFPackage aPackage)
    {
        _package = aPackage;
        for(QMFProperty prop : _properties.values())
        {
            prop.setQMFClass(this);
        }
        // TODO Statisics, Methods
    }

    public Type getType()
    {
        return _type;
    }

    public QMFPackage getPackage()
    {
        return _package;
    }

    public String getName()
    {
        return _name;
    }

    public byte[] getSchemaHash()
    {
        return _schemaHash;
    }

    public Collection<QMFProperty> getProperties()
    {
        return _properties.values();
    }

    public Collection<QMFStatistic> getStatistics()
    {
        return _statistics.values();
    }

    public Collection<QMFMethod> getMethods()
    {
        return _methods.values();
    }

    public QMFMethod getMethod(String methodName)
    {
        return _methods.get(methodName);
    }

}
