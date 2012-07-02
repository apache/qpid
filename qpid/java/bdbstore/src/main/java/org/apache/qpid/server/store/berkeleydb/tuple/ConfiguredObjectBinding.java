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
package org.apache.qpid.server.store.berkeleydb.tuple;

import org.apache.qpid.server.store.ConfiguredObjectRecord;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class ConfiguredObjectBinding extends TupleBinding<ConfiguredObjectRecord>
{
    private static final ConfiguredObjectBinding INSTANCE = new ConfiguredObjectBinding();

    public static ConfiguredObjectBinding getInstance()
    {
        return INSTANCE;
    }

    /** non-public constructor forces getInstance instead */
    private ConfiguredObjectBinding()
    {
    }

    public ConfiguredObjectRecord entryToObject(TupleInput tupleInput)
    {
        String type = tupleInput.readString();
        String json = tupleInput.readString();
        ConfiguredObjectRecord configuredObject = new ConfiguredObjectRecord(null, type, json);
        return configuredObject;
    }

    public void objectToEntry(ConfiguredObjectRecord object, TupleOutput tupleOutput)
    {
        tupleOutput.writeString(object.getType());
        tupleOutput.writeString(object.getAttributes());
    }

}
