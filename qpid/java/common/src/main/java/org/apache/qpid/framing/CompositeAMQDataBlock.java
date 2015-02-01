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
package org.apache.qpid.framing;

import java.io.DataOutput;
import java.io.IOException;

import org.apache.qpid.transport.ByteBufferSender;

public class CompositeAMQDataBlock extends AMQDataBlock implements EncodableAMQDataBlock
{

    private AMQDataBlock[] _blocks;

    public CompositeAMQDataBlock(AMQDataBlock[] blocks)
    {
        _blocks = blocks;
    }


    public AMQDataBlock[] getBlocks()
    {
        return _blocks;
    }


    public long getSize()
    {
        long frameSize = 0;
        for (int i = 0; i < _blocks.length; i++)
        {
            frameSize += _blocks[i].getSize();
        }
        return frameSize;
    }

    public void writePayload(DataOutput buffer) throws IOException
    {
        for (int i = 0; i < _blocks.length; i++)
        {
            _blocks[i].writePayload(buffer);
        }
    }

    @Override
    public long writePayload(final ByteBufferSender sender) throws IOException
    {
        long size = 0l;
        for (int i = 0; i < _blocks.length; i++)
        {
            size += _blocks[i].writePayload(sender);
        }
        return size;
    }

    public String toString()
    {
        if (_blocks == null)
        {
            return "No blocks contained in composite frame";
        }
        else
        {
            StringBuilder buf = new StringBuilder(this.getClass().getName());
            buf.append("{");
            for (int i = 0 ; i < _blocks.length; i++)
            {
                buf.append(" ").append(i).append("=[").append(_blocks[i].toString()).append("]");
            }
            buf.append("}");
            return buf.toString();
        }
    }
}
