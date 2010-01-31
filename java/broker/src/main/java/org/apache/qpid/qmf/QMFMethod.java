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
import org.apache.qpid.transport.codec.BBDecoder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;

public abstract class QMFMethod<T extends QMFObject>
{
    private final LinkedHashMap<String,Object> _map = new LinkedHashMap<String,Object>();
    private final List<Argument> _arguments = new ArrayList<Argument>();

    private static final String NAME = "name";
    private static final String TYPE = "type";
    private static final String REF_PACKAGE = "refPackage";
    private static final String REF_CLASS = "refClass";
    private static final String UNIT = "unit";
    private static final String MIN = "min";
    private static final String MAX = "max";
    private static final String MAX_LENGTH = "maxlen";
    private static final String DESCRIPTION = "desc";
    private static final String DEFAULT = "default";
    private static final String DIRECTION = "dir";
    private static final String ARG_COUNT = "argCount";



    public enum Direction
    {
        I,
        O,
        IO;
    }

    public class Argument
    {
        private final LinkedHashMap<String,Object> _map = new LinkedHashMap<String,Object>();

        public Argument(String name, QMFType type)
        {
            _map.put(NAME, name);
            _map.put(TYPE, type.codeValue());
        }

        public void setRefPackage(String refPackage)
        {
            _map.put(REF_PACKAGE, refPackage);
        }

        public void setRefClass(String refClass)
        {
            _map.put(REF_CLASS, refClass);
        }

        public void setUnit(String unit)
        {
            _map.put(UNIT, unit);
        }

        public void setMax(Number max)
        {
            _map.put(MAX, max);
        }

        public void setMin(Number min)
        {
            _map.put(MIN, min);
        }

        public void setMaxLength(int len)
        {
            _map.put(MAX_LENGTH, len);
        }

        public void setDefault(Object dflt)
        {
            _map.put(DEFAULT, dflt);
        }

        public void setDescription(String desc)
        {
            _map.put(DESCRIPTION, desc);
        }

        public void setDirection(Direction direction)
        {
            _map.put(DIRECTION, direction.toString());
        }

        public void encode(Encoder encoder)
        {
            encoder.writeMap(_map);
        }

        public String getName()
        {
            return (String) _map.get(NAME);
        }
    }

    public QMFMethod(String name, String description)
    {
        _map.put(NAME, name);
        _map.put(ARG_COUNT, 0);
        if(description != null)
        {
            _map.put(DESCRIPTION, description);
        }

    }

    abstract public QMFMethodInvocation<T> parse(final BBDecoder decoder);

    protected void addArgument(Argument arg)
    {
        _arguments.add(arg);
        _map.put(ARG_COUNT, _arguments.size());
    }


    public void encode(Encoder encoder)
    {
        encoder.writeMap(_map);
        for(Argument arg : _arguments)
        {
            arg.encode(encoder);
        }
    }

    public String getName()
    {
        return (String) _map.get(NAME);
    }
}
