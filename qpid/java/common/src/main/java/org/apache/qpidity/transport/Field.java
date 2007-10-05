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
package org.apache.qpidity.transport;

import org.apache.qpidity.transport.codec.Decoder;
import org.apache.qpidity.transport.codec.Encoder;


/**
 * Field
 *
 */

public abstract class Field<T>
{

    private final Class<T> container;
    private final String name;
    private final int index;

    Field(Class<T> container, String name, int index)
    {
        this.container = container;
        this.name = name;
        this.index = index;
    }

    public Class<T> getContainer()
    {
        return container;
    }

    public String getName()
    {
        return name;
    }

    public int getIndex()
    {
        return index;
    }

    protected T check(Object struct)
    {
        return container.cast(struct);
    }

    public abstract Object get(Object struct);

    public abstract void read(Decoder dec, Object struct);

    public abstract void write(Encoder enc, Object struct);

}
