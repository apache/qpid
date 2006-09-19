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
package org.apache.qpid.client.protocol;

import org.apache.qpid.framing.AMQMethodBody;

public class AMQMethodEvent
{
    private AMQMethodBody _method;

    private int _channelId;
    
    private AMQProtocolSession _protocolSession;

    public AMQMethodEvent(int channelId, AMQMethodBody method, AMQProtocolSession protocolSession)
    {
        _channelId = channelId;
        _method = method;
        _protocolSession = protocolSession;
    }

    public AMQMethodBody getMethod()
    {
        return _method;
    }

    public int getChannelId()
    {
        return _channelId;
    }
    
    public AMQProtocolSession getProtocolSession()
    {
        return _protocolSession;
    }
    
    public String toString()
    {
        StringBuffer buf = new StringBuffer("Method event: ");
        buf.append("\nChannel id: ").append(_channelId);
        buf.append("\nMethod: ").append(_method);
        return buf.toString();
    }
}
