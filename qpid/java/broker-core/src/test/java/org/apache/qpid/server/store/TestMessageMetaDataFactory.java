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
package org.apache.qpid.server.store;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.qpid.framing.EncodingUtils;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.util.ByteBufferInputStream;

public class TestMessageMetaDataFactory implements MessageMetaDataType.Factory<TestMessageMetaData>
{
    public TestMessageMetaData createMetaData(ByteBuffer buf)
    {
        try
        {
            ByteBufferInputStream bbis = new ByteBufferInputStream(buf);
            DataInputStream dais = new DataInputStream(bbis);

            long id = EncodingUtils.readLong(dais);
            int size = EncodingUtils.readInteger(dais);

            return new TestMessageMetaData(id, size);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

    }
}