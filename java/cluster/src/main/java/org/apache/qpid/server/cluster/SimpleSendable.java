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
package org.apache.qpid.server.cluster;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.AMQFrame;

import java.util.Arrays;
import java.util.List;

public class SimpleSendable implements Sendable
{
    private final List<AMQBody> _bodies;

    public SimpleSendable(AMQBody body)
    {
        this(Arrays.asList(body));
    }

    public SimpleSendable(List<AMQBody> bodies)
    {
        _bodies = bodies;
    }

    public void send(int channel, Member member) throws AMQException
    {
        for (AMQBody body : _bodies)
        {
            member.send(new AMQFrame(channel, body));
        }
    }

    public String toString()
    {
        return _bodies.toString();
    }
}
