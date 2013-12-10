/*
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
 */

package org.apache.qpid.amqp_1_0.codec;

import java.nio.ByteBuffer;

public class ArrayWriter  implements ValueWriter<Object[]>
{
    private Object[] _list;
    private int _position = 0;
    private final Registry _registry;
    private ValueWriter valueWriter;

    public ArrayWriter(final Registry registry)
    {
        _registry = registry;
    }


    protected void onSetValue(final Object[] value)
    {

        Class clazz = value.getClass().getComponentType();
        //valueWriter = _registry.getValueWriterByClass(clazz);


    }




    private static Factory<Object[]> FACTORY = new Factory<Object[]>()
                                            {

                                                public ValueWriter<Object[]> newInstance(Registry registry)
                                                {
                                                    return new ArrayWriter(registry);
                                                }
                                            };

    public static void register(Registry registry)
    {
        //registry.register(List.class, FACTORY);
    }

    public int writeToBuffer(final ByteBuffer buffer)
    {
        return 0;  //TODO change body of implemented methods use File | Settings | File Templates.
    }

    public void setValue(final Object[] frameBody)
    {
        //TODO change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isComplete()
    {
        return false;  //TODO change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isCacheable()
    {
        return false;  //TODO change body of implemented methods use File | Settings | File Templates.
    }
}