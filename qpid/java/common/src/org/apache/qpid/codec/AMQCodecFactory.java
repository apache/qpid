/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.codec;

import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;

public class AMQCodecFactory implements ProtocolCodecFactory
{
    private AMQEncoder _encoder = new AMQEncoder();

    private AMQDecoder _frameDecoder;

    /**
     * @param expectProtocolInitiation true if the first frame received is going to be
     * a protocol initiation frame, false if it is going to be a standard AMQ data block.
     * The former case is used for the broker, which always expects to received the
     * protocol initiation first from a newly connected client.
     */
    public AMQCodecFactory(boolean expectProtocolInitiation)
    {
        _frameDecoder = new AMQDecoder(expectProtocolInitiation);
    }

    public ProtocolEncoder getEncoder()
    {
        return _encoder;
    }

    public ProtocolDecoder getDecoder()
    {
        return _frameDecoder;
    }
}
