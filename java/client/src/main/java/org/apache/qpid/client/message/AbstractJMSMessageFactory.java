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
package org.apache.qpid.client.message;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;

import javax.jms.JMSException;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractJMSMessageFactory implements MessageFactory
{
    private static final Logger _logger = Logger.getLogger(AbstractJMSMessageFactory.class);


    protected abstract AbstractJMSMessage createMessage(long messageNbr, ByteBuffer data,
                                                                ContentHeaderBody contentHeader) throws AMQException;

    protected AbstractJMSMessage createMessageWithBody(long messageNbr,
                                                       ContentHeaderBody contentHeader,
                                                       List bodies) throws AMQException
    {
        ByteBuffer data;

        // we optimise the non-fragmented case to avoid copying
        if (bodies != null && bodies.size() == 1)
        {
            _logger.debug("Non-fragmented message body (bodySize=" + contentHeader.bodySize +")");
            data = ((ContentBody)bodies.get(0)).payload;
        }
        else
        {
            _logger.debug("Fragmented message body (" + bodies.size() + " frames, bodySize=" + contentHeader.bodySize + ")");
            data = ByteBuffer.allocate((int)contentHeader.bodySize); // XXX: Is cast a problem?
            final Iterator it = bodies.iterator();
            while (it.hasNext())
            {
                ContentBody cb = (ContentBody) it.next();
                data.put(cb.payload);
                cb.payload.release();
            }
            data.flip();
        }
        _logger.debug("Creating message from buffer with position=" + data.position() + " and remaining=" + data.remaining());

        return createMessage(messageNbr, data, contentHeader);
    }

    public AbstractJMSMessage createMessage(long messageNbr, boolean redelivered,
                                            ContentHeaderBody contentHeader,
                                            List bodies) throws JMSException, AMQException
    {
        final AbstractJMSMessage msg = createMessageWithBody(messageNbr, contentHeader, bodies);
        msg.setJMSRedelivered(redelivered);
        return msg;
    }

}
