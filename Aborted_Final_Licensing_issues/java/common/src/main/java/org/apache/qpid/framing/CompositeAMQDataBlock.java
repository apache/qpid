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

import org.apache.mina.common.ByteBuffer;

public class CompositeAMQDataBlock extends AMQDataBlock implements EncodableAMQDataBlock
{
    private ByteBuffer _encodedBlock;

    private AMQDataBlock[] _blocks;

    public CompositeAMQDataBlock(AMQDataBlock[] blocks)
    {
        _blocks = blocks;
    }

    /**
     * The encoded block will be logically first before the AMQDataBlocks which are encoded
     * into the buffer afterwards.
     * @param encodedBlock already-encoded data
     * @param blocks some blocks to be encoded.
     */
    public CompositeAMQDataBlock(ByteBuffer encodedBlock, AMQDataBlock[] blocks)
    {
        this(blocks);
        _encodedBlock = encodedBlock;
    }

    public AMQDataBlock[] getBlocks()
    {
        return _blocks;
    }

    public ByteBuffer getEncodedBlock()
    {
        return _encodedBlock;
    }

    public long getSize()
    {
        long frameSize = 0;
        for (int i = 0; i < _blocks.length; i++)
        {
            frameSize += _blocks[i].getSize();
        }
        if (_encodedBlock != null)
        {
            _encodedBlock.rewind();
            frameSize += _encodedBlock.remaining();
        }
        return frameSize;
    }

    public void writePayload(ByteBuffer buffer)
    {
        if (_encodedBlock != null)
        {
            buffer.put(_encodedBlock);
        }
        for (int i = 0; i < _blocks.length; i++)
        {
            _blocks[i].writePayload(buffer);
        }
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
            buf.append("{encodedBlock=").append(_encodedBlock);
            for (int i = 0 ; i < _blocks.length; i++)
            {
                buf.append(" ").append(i).append("=[").append(_blocks[i].toString()).append("]");
            }
            buf.append("}");
            return buf.toString();
        }
    }
}
