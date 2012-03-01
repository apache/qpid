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

import java.nio.ByteBuffer;

public class ContentTB extends TupleBinding
{
    public Object entryToObject(TupleInput tupleInput)
    {

        final int size = tupleInput.readInt();
        byte[] underlying = new byte[size];
        tupleInput.readFast(underlying);
        return ByteBuffer.wrap(underlying);
    }

    public void objectToEntry(Object object, TupleOutput tupleOutput)
    {
        ByteBuffer src = (ByteBuffer) object;
        
        src = src.slice();

        byte[] chunkData = new byte[src.limit()];
        src.duplicate().get(chunkData);

        tupleOutput.writeInt(chunkData.length);
        tupleOutput.writeFast(chunkData);
    }
}
