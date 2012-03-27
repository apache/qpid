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

import java.nio.ByteBuffer;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

import org.apache.qpid.server.store.MessageMetaDataType;
import org.apache.qpid.server.store.StorableMessageMetaData;

/**
 * Handles the mapping to and from message meta data
 */
public class MessageMetaDataBinding extends TupleBinding<StorableMessageMetaData>
{
    private static final MessageMetaDataBinding INSTANCE = new MessageMetaDataBinding();

    public static MessageMetaDataBinding getInstance()
    {
        return INSTANCE;
    }

    /** private constructor forces getInstance instead */
    private MessageMetaDataBinding() { }

    @Override
    public StorableMessageMetaData entryToObject(TupleInput tupleInput)
    {
        final int bodySize = tupleInput.readInt();
        byte[] dataAsBytes = new byte[bodySize];
        tupleInput.readFast(dataAsBytes);

        ByteBuffer buf = ByteBuffer.wrap(dataAsBytes);
        buf.position(1);
        buf = buf.slice();
        MessageMetaDataType type = MessageMetaDataType.values()[dataAsBytes[0]];
        StorableMessageMetaData metaData = type.getFactory().createMetaData(buf);

        return metaData;
    }

    @Override
    public void objectToEntry(StorableMessageMetaData metaData, TupleOutput tupleOutput)
    {
        final int bodySize = 1 + metaData.getStorableSize();
        byte[] underlying = new byte[bodySize];
        underlying[0] = (byte) metaData.getType().ordinal();
        ByteBuffer buf = ByteBuffer.wrap(underlying);
        buf.position(1);
        buf = buf.slice();

        metaData.writeToBuffer(0, buf);
        tupleOutput.writeInt(bodySize);
        tupleOutput.writeFast(underlying);
    }
}
