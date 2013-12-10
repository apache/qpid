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

package org.apache.qpid.amqp_1_0.codec;

import java.util.Iterator;
import java.util.Map;

public class MapWriter extends AbstractMapWriter<Map>
{
    private Map _map;
    private Object _value;
    private Iterator<Map.Entry> _iterator;

    public MapWriter(final Registry registry)
    {
        super(registry);
    }

    @Override
    protected void onSetValue(final Map value)
    {
        _map = value;
        _iterator = value.entrySet().iterator();
    }

    @Override
    protected int getMapCount()
    {
        return _map.size();
    }

    @Override
    protected boolean hasMapNext()
    {
        return _iterator.hasNext();
    }

    @Override
    protected Object nextKey()
    {
        Map.Entry entry = _iterator.next();
        _value = entry.getValue();
        return entry.getKey();
    }
    @Override
    protected Object nextValue()
    {
        Object value = _value;
        _value = null;
        return value;
    }


    @Override
    protected void onClear()
    {
        _map = null;
        _iterator = null;
        _value = null;
    }

    @Override
    protected void onReset()
    {
        _iterator = _map.entrySet().iterator();
        _value = null;
    }


    private static Factory<Map> FACTORY = new Factory<Map>()
                                            {

                                                public ValueWriter<Map> newInstance(Registry registry)
                                                {
                                                    return new MapWriter(registry);
                                                }
                                            };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(Map.class, FACTORY);
    }
}