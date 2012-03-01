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

package org.apache.qpid.server.store.berkeleydb.tuples;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import org.apache.qpid.server.store.berkeleydb.keys.Xid;

public class XidTB extends TupleBinding<Xid>
{
    @Override
    public Xid entryToObject(TupleInput input)
    {
        long format = input.readLong();
        byte[] globalId = new byte[input.readInt()];
        input.readFast(globalId);
        byte[] branchId = new byte[input.readInt()];
        input.readFast(branchId);
        return new Xid(format,globalId,branchId);
    }

    @Override
    public void objectToEntry(Xid xid, TupleOutput output)
    {
        output.writeLong(xid.getFormat());
        output.writeInt(xid.getGlobalId() == null ? 0 : xid.getGlobalId().length);
        if(xid.getGlobalId() != null)
        {
            output.write(xid.getGlobalId());
        }
        output.writeInt(xid.getBranchId() == null ? 0 : xid.getBranchId().length);
        if(xid.getBranchId() != null)
        {
            output.write(xid.getBranchId());
        }

    }
}
