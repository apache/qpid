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
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.*;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.message.MessageMetaData;
import org.apache.qpid.server.store.berkeleydb.AMQShortStringEncoding;

import java.io.*;

/**
 * Handles the mapping to and from 0-8/0-9 message meta data
 */
public class MessageMetaDataTB_4 extends TupleBinding<Object>
{
    private static final Logger _log = Logger.getLogger(MessageMetaDataTB_4.class);

    public MessageMetaDataTB_4()
    {
    }

    public Object entryToObject(TupleInput tupleInput)
    {
        try
        {
            final MessagePublishInfo publishBody = readMessagePublishInfo(tupleInput);
            final ContentHeaderBody contentHeaderBody = readContentHeaderBody(tupleInput);
            final int contentChunkCount = tupleInput.readInt();

            return new MessageMetaData(publishBody, contentHeaderBody, contentChunkCount);
        }
        catch (Exception e)
        {
            _log.error("Error converting entry to object: " + e, e);
            // annoyingly just have to return null since we cannot throw
            return null;
        }
    }

    public void objectToEntry(Object object, TupleOutput tupleOutput)
    {
        MessageMetaData message = (MessageMetaData) object;
        try
        {
            writeMessagePublishInfo(message.getMessagePublishInfo(), tupleOutput);
        }
        catch (AMQException e)
        {
            // can't do anything else since the BDB interface precludes throwing any exceptions
            // in practice we should never get an exception
            throw new RuntimeException("Error converting object to entry: " + e, e);
        }
        writeContentHeader(message.getContentHeaderBody(), tupleOutput);
        tupleOutput.writeInt(message.getContentChunkCount());
    }

    private MessagePublishInfo readMessagePublishInfo(TupleInput tupleInput)
    {

        final AMQShortString exchange = AMQShortStringEncoding.readShortString(tupleInput);
        final AMQShortString routingKey = AMQShortStringEncoding.readShortString(tupleInput);
        final boolean mandatory = tupleInput.readBoolean();
        final boolean immediate = tupleInput.readBoolean();

        return new MessagePublishInfo()
        {

            public AMQShortString getExchange()
            {
                return exchange;
            }

            public void setExchange(AMQShortString exchange)
            {

            }

            public boolean isImmediate()
            {
                return immediate;
            }

            public boolean isMandatory()
            {
                return mandatory;
            }

            public AMQShortString getRoutingKey()
            {
                return routingKey;
            }
        }   ;

    }

    private ContentHeaderBody readContentHeaderBody(TupleInput tupleInput) throws AMQFrameDecodingException, AMQProtocolVersionException
    {
        int bodySize = tupleInput.readInt();
        byte[] underlying = new byte[bodySize];
        tupleInput.readFast(underlying);

        try
        {
            return ContentHeaderBody.createFromBuffer(new DataInputStream(new ByteArrayInputStream(underlying)), bodySize);
        }
        catch (IOException e)
        {
            throw new AMQFrameDecodingException(null, e.getMessage(), e);
        }
    }

    private void writeMessagePublishInfo(MessagePublishInfo publishBody, TupleOutput tupleOutput) throws AMQException
    {

        AMQShortStringEncoding.writeShortString(publishBody.getExchange(), tupleOutput);
        AMQShortStringEncoding.writeShortString(publishBody.getRoutingKey(), tupleOutput);
        tupleOutput.writeBoolean(publishBody.isMandatory());
        tupleOutput.writeBoolean(publishBody.isImmediate());
    }

    private void writeContentHeader(ContentHeaderBody headerBody, TupleOutput tupleOutput)
    {
        // write out the content header body
        final int bodySize = headerBody.getSize();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(bodySize);
        try
        {
            headerBody.writePayload(new DataOutputStream(baos));
            tupleOutput.writeInt(bodySize);
            tupleOutput.writeFast(baos.toByteArray());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

    }
}
