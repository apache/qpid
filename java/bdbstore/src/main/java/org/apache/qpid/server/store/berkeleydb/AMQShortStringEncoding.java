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

import org.apache.qpid.framing.AMQShortString;

import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class AMQShortStringEncoding
{
    public static AMQShortString readShortString(TupleInput tupleInput)
    {
        int length = (int) tupleInput.readShort();
        if (length < 0)
        {
            return null;
        }
        else
        {
            byte[] stringBytes = new byte[length];
            tupleInput.readFast(stringBytes);
            return new AMQShortString(stringBytes);
        }

    }

    public  static void writeShortString(AMQShortString shortString, TupleOutput tupleOutput)
    {

        if (shortString == null)
        {
            tupleOutput.writeShort(-1);
        }
        else
        {
            tupleOutput.writeShort(shortString.length());
            tupleOutput.writeFast(shortString.getBytes(), 0, shortString.length());
        }
    }
}
