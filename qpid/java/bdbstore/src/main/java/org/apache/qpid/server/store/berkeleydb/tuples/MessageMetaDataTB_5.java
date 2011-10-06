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

import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import org.apache.log4j.Logger;
import org.apache.qpid.server.store.MessageMetaDataType;
import org.apache.qpid.server.store.StorableMessageMetaData;

/**
 * Handles the mapping to and from message meta data
 */
public class MessageMetaDataTB_5 extends MessageMetaDataTB_4
{
    private static final Logger _log = Logger.getLogger(MessageMetaDataTB_5.class);

    @Override
    public Object entryToObject(TupleInput tupleInput)
    {
        try
        {
            final int bodySize = tupleInput.readInt();
            byte[] dataAsBytes = new byte[bodySize];
            tupleInput.readFast(dataAsBytes);

            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(dataAsBytes);
            buf.position(1);
            buf = buf.slice();
            MessageMetaDataType type = MessageMetaDataType.values()[dataAsBytes[0]];
            StorableMessageMetaData metaData = type.getFactory().createMetaData(buf);

            return metaData;
        }
        catch (Exception e)
        {
            _log.error("Error converting entry to object: " + e, e);
            // annoyingly just have to return null since we cannot throw
            return null;
        }
    }

    @Override
    public void objectToEntry(Object object, TupleOutput tupleOutput)
    {
        StorableMessageMetaData metaData = (StorableMessageMetaData) object;

        final int bodySize = 1 + metaData.getStorableSize();
        byte[] underlying = new byte[bodySize];
        underlying[0] = (byte) metaData.getType().ordinal();
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(underlying);
        buf.position(1);
        buf = buf.slice();

        metaData.writeToBuffer(0, buf);
        tupleOutput.writeInt(bodySize);
        tupleOutput.writeFast(underlying);
    }
}
