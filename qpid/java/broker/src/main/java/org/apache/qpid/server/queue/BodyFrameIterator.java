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
package org.apache.qpid.server.queue;

import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.abstraction.ProtocolVersionMethodConverter;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.AMQException;
import org.apache.log4j.Logger;

import java.util.Iterator;


public class BodyFrameIterator implements Iterator<AMQDataBlock>
{
    private static final Logger _log = Logger.getLogger(BodyFrameIterator.class);


    private int _channel;

    private int _index = -1;
    private AMQProtocolSession _protocolSession;
    private BodyContentHolder _bodyContentHolder;

    public BodyFrameIterator(AMQProtocolSession protocolSession, int channel, BodyContentHolder messageHandle)
    {
        _channel = channel;
        _protocolSession = protocolSession;
        _bodyContentHolder = messageHandle;
    }

    public boolean hasNext()
    {
        try
        {
            return _index < (_bodyContentHolder.getBodyCount() - 1);
        }
        catch (AMQException e)
        {
            _log.error("Unable to get body count: " + e, e);

            return false;
        }
    }

    public AMQDataBlock next()
    {
        try
        {

            AMQBody cb =
                    getProtocolVersionMethodConverter().convertToBody(_bodyContentHolder.getContentChunk(
                            ++_index));

            return new AMQFrame(_channel, cb);
        }
        catch (AMQException e)
        {
            // have no choice but to throw a runtime exception
            throw new RuntimeException("Error getting content body: " + e, e);
        }

    }

    private ProtocolVersionMethodConverter getProtocolVersionMethodConverter()
    {
        return _protocolSession.getMethodRegistry().getProtocolVersionMethodConverter();
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }
}
