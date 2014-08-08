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

import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;

/**
 * AMQCodecFactory is a Mina codec factory. It supplies the encoders and decoders need to read and write the bytes to
 * the wire.
 */
public class AMQCodecFactory
{

    /** Holds the protocol decoder. */
    private final AMQDecoder _frameDecoder;

    /**
     * Creates a new codec factory, specifiying whether it is expected that the first frame of data should be an
     * initiation. This is the case for the broker, which always expects to received the protocol initiation on a newly
     * connected client.
     *
     * @param expectProtocolInitiation <tt>true</tt> if the first frame received is going to be a protocol initiation
     *                                 frame, <tt>false</tt> if it is going to be a standard AMQ data block.
     * @param session protocol session (connection)
     */
    public AMQCodecFactory(boolean expectProtocolInitiation, AMQVersionAwareProtocolSession session)
    {
        _frameDecoder = new AMQDecoder(expectProtocolInitiation, session);
    }


    /**
     * Gets the AMQP decoder.
     *
     * @return The AMQP decoder.
     */
    public AMQDecoder getDecoder()
    {
        return _frameDecoder;
    }
}
