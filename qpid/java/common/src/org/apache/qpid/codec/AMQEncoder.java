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

import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.common.IoSession;
import org.apache.qpid.framing.AMQDataBlockEncoder;

public class AMQEncoder implements ProtocolEncoder
{
    private AMQDataBlockEncoder _dataBlockEncoder = new AMQDataBlockEncoder();

    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception
    {
        _dataBlockEncoder.encode(session, message, out);
    }

    public void dispose(IoSession session) throws Exception
    {

    }
}
