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

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.filter.codec.demux.MessageEncoder;

import java.util.HashSet;
import java.util.Set;

public class AMQDataBlockEncoder implements MessageEncoder
{
	Logger _logger = Logger.getLogger(AMQDataBlockEncoder.class);

    private Set _messageTypes;

    public AMQDataBlockEncoder()
    {
        _messageTypes = new HashSet();
        _messageTypes.add(EncodableAMQDataBlock.class);
    }

    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception
    {
        final AMQDataBlock frame = (AMQDataBlock) message;
        int frameSize = (int)frame.getSize();
        final ByteBuffer buffer = ByteBuffer.allocate(frameSize);
        //buffer.setAutoExpand(true);
        frame.writePayload(buffer);

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Encoded frame byte-buffer is '" + EncodingUtils.convertToHexString(buffer) + "'");
        }

        buffer.flip();
        out.write(buffer);
    }

    public Set getMessageTypes()
    {
        return _messageTypes;
    }
}
