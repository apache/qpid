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

import java.nio.ByteBuffer;
import java.util.Map;

public interface ValueWriter<T extends Object>
{



    public static interface Factory<V extends Object>
    {
        ValueWriter<V> newInstance(Registry registry);
    }

    public static interface Registry
    {
        public static interface Source
        {
            public Registry getDescribedTypeRegistry();
        }

        <V extends Object> ValueWriter<V> getValueWriter(V value);
        <V extends Object> ValueWriter<V> getValueWriter(V value, Map<Class, ValueWriter> localCache);
        <V extends Object> ValueWriter<V> register(Class<V> clazz, ValueWriter.Factory<V> writer);

    }


    int writeToBuffer(ByteBuffer buffer);

    void setValue(T frameBody);

    boolean isComplete();

    boolean isCacheable();
}
