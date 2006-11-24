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
package org.apache.qpid.codec;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.qpid.framing.AMQDataBlockDecoder;
import org.apache.qpid.framing.ProtocolInitiation;

/**
 * There is one instance of this class per session. Any changes or configuration done
 * at run time to the encoders or decoders only affects decoding/encoding of the
 * protocol session data to which is it bound.
 *
 */
public class AMQDecoder extends CumulativeProtocolDecoder
{
    private AMQDataBlockDecoder _dataBlockDecoder = new AMQDataBlockDecoder();

    private ProtocolInitiation.Decoder _piDecoder = new ProtocolInitiation.Decoder();

    private boolean _expectProtocolInitiation;

    public AMQDecoder(boolean expectProtocolInitiation)
    {
        _expectProtocolInitiation = expectProtocolInitiation;
    }

    protected boolean doDecode(IoSession session, ByteBuffer in, ProtocolDecoderOutput out) throws Exception
    {
        if (_expectProtocolInitiation)
        {
            return doDecodePI(session, in, out);
        }
        else
        {
            return doDecodeDataBlock(session, in, out);
        }
    }

    protected boolean doDecodeDataBlock(IoSession session, ByteBuffer in, ProtocolDecoderOutput out) throws Exception
    {
        int pos = in.position();
        boolean enoughData = _dataBlockDecoder.decodable(session, in);
        in.position(pos);
        if (!enoughData)
        {
            // returning false means it will leave the contents in the buffer and
            // call us again when more data has been read
            return false;
        }
        else
        {
            _dataBlockDecoder.decode(session, in, out);
            return true;
        }
    }

    private boolean doDecodePI(IoSession session, ByteBuffer in, ProtocolDecoderOutput out) throws Exception
    {
        boolean enoughData = _piDecoder.decodable(session, in);
        if (!enoughData)
        {
            // returning false means it will leave the contents in the buffer and
            // call us again when more data has been read
            return false;
        }
        else
        {
            _piDecoder.decode(session, in, out);
            return true;
        }
    }

    public void setExpectProtocolInitiation(boolean expectProtocolInitiation)
    {
        _expectProtocolInitiation = expectProtocolInitiation;
    }
}
