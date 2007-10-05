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

import java.util.List;

import org.apache.qpidity.transport.codec.Encodable;


/**
 * Struct
 *
 * @author Rafael H. Schloming
 */

public abstract class Struct implements Encodable
{

    public static Struct create(int type)
    {
        return StructFactory.create(type);
    }

    public abstract List<Field<?>> getFields();

    public abstract int getEncodedType();

    public String toString()
    {
        StringBuilder str = new StringBuilder();
        str.append(getClass().getSimpleName());

        str.append("(");
        boolean first = true;
        for (Field<?> f : getFields())
        {
            if (first)
            {
                first = false;
            }
            else
            {
                str.append(", ");
            }
            str.append(f.getName());
            str.append("=");
            str.append(f.get(this));
        }
        str.append(")");

        return str.toString();
    }

}
