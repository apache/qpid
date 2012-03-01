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
package org.apache.qpid.server.store.berkeleydb;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

import java.util.HashMap;
import java.util.Map;

public class StringMapBinding extends TupleBinding<Map<String,String>>
{
    
    private static final StringMapBinding INSTANCE = new StringMapBinding();
    
    public Map<String, String> entryToObject(final TupleInput tupleInput)
    {
        int entries = tupleInput.readInt();
        Map<String,String> map = new HashMap<String,String>(entries);
        for(int i = 0; i < entries; i++)
        {
            map.put(tupleInput.readString(), tupleInput.readString());
        }
        return map;
    }

    
    public void objectToEntry(final Map<String, String> stringStringMap, final TupleOutput tupleOutput)
    {
        tupleOutput.writeInt(stringStringMap.size());
        for(Map.Entry<String,String> entry : stringStringMap.entrySet())
        {
            tupleOutput.writeString(entry.getKey());
            tupleOutput.writeString(entry.getValue());
        }
    }

    public static StringMapBinding getInstance()
    {
        return INSTANCE;
    }
}
