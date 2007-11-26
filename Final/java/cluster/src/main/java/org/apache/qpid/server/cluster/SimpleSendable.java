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
package org.apache.qpid.server.cluster;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.MethodConverter_8_0;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.ProtocolVersionMethodConverter;
import org.apache.qpid.server.queue.AMQMessage;

import java.util.Iterator;

public class SimpleSendable implements Sendable
{

    //todo fixme - remove 0-8 hard coding
    ProtocolVersionMethodConverter _methodConverter = new MethodConverter_8_0();

    private final AMQMessage _message;

    public SimpleSendable(AMQMessage message)
    {
        _message = message;
    }

    public void send(int channel, Member member) throws AMQException
    {
        member.send(new AMQFrame(channel, _methodConverter.convertToBody(_message.getMessagePublishInfo())));
        member.send(new AMQFrame(channel, _message.getContentHeaderBody()));
        Iterator<ContentChunk> it = _message.getContentBodyIterator();
        while (it.hasNext())
        {
            member.send(new AMQFrame(channel, _methodConverter.convertToBody(it.next())));
        }
    }
}
