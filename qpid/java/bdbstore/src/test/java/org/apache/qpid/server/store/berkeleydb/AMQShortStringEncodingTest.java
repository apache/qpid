/*
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
 */
package org.apache.qpid.server.store.berkeleydb;

import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import junit.framework.TestCase;

import org.apache.qpid.framing.AMQShortString;

/**
 * Tests for {@code AMQShortStringEncoding} including corner cases when string
 * is null or over 127 characters in length
 */
public class AMQShortStringEncodingTest extends TestCase
{

    public void testWriteReadNullValues()
    {
        // write into tuple output
        TupleOutput tupleOutput = new TupleOutput();
        AMQShortStringEncoding.writeShortString(null, tupleOutput);
        byte[] data = tupleOutput.getBufferBytes();

        // read from tuple input
        TupleInput tupleInput = new TupleInput(data);
        AMQShortString result = AMQShortStringEncoding.readShortString(tupleInput);
        assertNull("Expected null but got " + result, result);
    }

    public void testWriteReadShortStringWithLengthOver127()
    {
        AMQShortString value = createString('a', 128);

        // write into tuple output
        TupleOutput tupleOutput = new TupleOutput();
        AMQShortStringEncoding.writeShortString(value, tupleOutput);
        byte[] data = tupleOutput.getBufferBytes();

        // read from tuple input
        TupleInput tupleInput = new TupleInput(data);
        AMQShortString result = AMQShortStringEncoding.readShortString(tupleInput);
        assertEquals("Expected " + value + " but got " + result, value, result);
    }

    public void testWriteReadShortStringWithLengthLess127()
    {
        AMQShortString value = new AMQShortString("test");

        // write into tuple output
        TupleOutput tupleOutput = new TupleOutput();
        AMQShortStringEncoding.writeShortString(value, tupleOutput);
        byte[] data = tupleOutput.getBufferBytes();

        // read from tuple input
        TupleInput tupleInput = new TupleInput(data);
        AMQShortString result = AMQShortStringEncoding.readShortString(tupleInput);
        assertEquals("Expected " + value + " but got " + result, value, result);
    }

    private AMQShortString createString(char ch, int length)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++)
        {
            sb.append(ch);
        }
        return new AMQShortString(sb.toString());
    }

}
