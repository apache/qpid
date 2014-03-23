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

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import org.apache.qpid.server.store.berkeleydb.entry.HierarchyKey;
import org.apache.qpid.server.store.berkeleydb.entry.QueueEntryKey;

import java.util.UUID;

public class HierarchyKeyBinding extends TupleBinding<HierarchyKey>
{

    private static final HierarchyKeyBinding INSTANCE = new HierarchyKeyBinding();

    public static HierarchyKeyBinding getInstance()
    {
        return INSTANCE;
    }

    /** private constructor forces getInstance instead */
    private HierarchyKeyBinding() { }

    public HierarchyKey entryToObject(TupleInput tupleInput)
    {
        UUID childId = new UUID(tupleInput.readLong(), tupleInput.readLong());
        String parentType = tupleInput.readString();

        return new HierarchyKey(childId, parentType);
    }

    public void objectToEntry(HierarchyKey hk, TupleOutput tupleOutput)
    {
        UUID uuid = hk.getChildId();
        tupleOutput.writeLong(uuid.getMostSignificantBits());
        tupleOutput.writeLong(uuid.getLeastSignificantBits());
        tupleOutput.writeString(hk.getParentType());
    }
}