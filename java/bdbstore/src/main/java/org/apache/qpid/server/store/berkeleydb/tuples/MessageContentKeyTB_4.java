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

import org.apache.qpid.server.store.berkeleydb.MessageContentKey;
import org.apache.qpid.server.store.berkeleydb.keys.MessageContentKey_4;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class MessageContentKeyTB_4 extends TupleBinding<MessageContentKey>
{

    public MessageContentKey entryToObject(TupleInput tupleInput)
    {
        long messageId = tupleInput.readLong();
        int chunk = tupleInput.readInt();
        return new MessageContentKey_4(messageId, chunk);
    }

    public void objectToEntry(MessageContentKey object, TupleOutput tupleOutput)
    {
        final MessageContentKey_4 mk = (MessageContentKey_4) object;
        tupleOutput.writeLong(mk.getMessageId());
        tupleOutput.writeInt(mk.getChunk());
    }

}