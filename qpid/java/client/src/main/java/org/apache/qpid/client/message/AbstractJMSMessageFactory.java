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
package org.apache.qpid.client.message;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.jms.JMSException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession_0_8;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.util.GZIPUtils;

public abstract class AbstractJMSMessageFactory implements MessageFactory
{
    private static final Logger _logger = LoggerFactory.getLogger(AbstractJMSMessageFactory.class);

    protected AbstractJMSMessage create08MessageWithBody(long messageNbr, ContentHeaderBody contentHeader,
                                                         AMQShortString exchange, AMQShortString routingKey,
                                                         List bodies,
                                                         AMQSession_0_8.DestinationCache<AMQQueue> queueDestinationCache,
                                                         AMQSession_0_8.DestinationCache<AMQTopic> topicDestinationCache) throws AMQException
    {
        ByteBuffer data;
        final boolean debug = _logger.isDebugEnabled();

        byte[] uncompressed;

        if(GZIPUtils.GZIP_CONTENT_ENCODING.equals(contentHeader.getProperties().getEncodingAsString())
                && (uncompressed = GZIPUtils.uncompressStreamToArray(new BodyInputStream(bodies))) != null )
        {
            contentHeader.getProperties().setEncoding((String)null);
            data = ByteBuffer.wrap(uncompressed);
        }
        else
        {
            // we optimise the non-fragmented case to avoid copying
            if ((bodies != null) && (bodies.size() == 1))
            {
                if (debug)
                {
                    _logger.debug("Non-fragmented message body (bodySize=" + contentHeader.getBodySize() + ")");
                }

                data = ByteBuffer.wrap(((ContentBody) bodies.get(0)).getPayload());
            }
            else if (bodies != null)
            {
                if (debug)
                {
                    _logger.debug("Fragmented message body (" + bodies
                            .size() + " frames, bodySize=" + contentHeader.getBodySize() + ")");
                }

                data = ByteBuffer.allocate((int) contentHeader.getBodySize()); // XXX: Is cast a problem?
                final Iterator it = bodies.iterator();
                while (it.hasNext())
                {
                    ContentBody cb = (ContentBody) it.next();
                    final ByteBuffer payload = ByteBuffer.wrap(cb.getPayload());
                    if (payload.isDirect() || payload.isReadOnly())
                    {
                        data.put(payload);
                    }
                    else
                    {
                        data.put(payload.array(), payload.arrayOffset(), payload.limit());
                    }

                }

                data.flip();
            }
            else // bodies == null
            {
                data = ByteBuffer.allocate(0);
            }
        }

        if (debug)
        {
            _logger.debug("Creating message from buffer with position=" + data.position() + " and remaining=" + data
                    .remaining());
        }

        AMQMessageDelegate delegate = new AMQMessageDelegate_0_8(messageNbr,
                                                                 contentHeader.getProperties(),
                                                                 exchange, routingKey, queueDestinationCache, topicDestinationCache);

        return createMessage(delegate, data);
    }

    protected abstract AbstractJMSMessage createMessage(AMQMessageDelegate delegate, ByteBuffer data) throws AMQException;


    protected AbstractJMSMessage create010MessageWithBody(long messageNbr, MessageProperties msgProps,
                                                          DeliveryProperties deliveryProps,
                                                          java.nio.ByteBuffer body) throws AMQException
    {
        ByteBuffer data;
        final boolean debug = _logger.isDebugEnabled();


        if (body != null)
        {
            data = body;
        }
        else // body == null
        {
            data = ByteBuffer.allocate(0);
        }

        if (debug)
        {
            _logger.debug("Creating message from buffer with position=" + data.position() + " and remaining=" + data
                    .remaining());
        }
        if(GZIPUtils.GZIP_CONTENT_ENCODING.equals(msgProps.getContentEncoding()))
        {
            byte[] uncompressed = GZIPUtils.uncompressBufferToArray(data);
            if(uncompressed != null)
            {
                msgProps.setContentEncoding(null);
                data = ByteBuffer.wrap(uncompressed);
            }
        }
        AMQMessageDelegate delegate = new AMQMessageDelegate_0_10(msgProps, deliveryProps, messageNbr);

        AbstractJMSMessage message = createMessage(delegate, data);
        return message;
    }

    private ByteBuffer uncompressBody(final InputStream bodyInputStream) throws AMQException
    {
        final ByteBuffer data;
        try(GZIPInputStream gzipInputStream = new GZIPInputStream(bodyInputStream))
        {
            ByteArrayOutputStream uncompressedBuffer = new ByteArrayOutputStream();
            int read;
            byte[] buf = new byte[4096];
            while((read = gzipInputStream.read(buf))!=-1)
            {
                uncompressedBuffer.write(buf,0,read);
            }
            byte[] uncompressedBytes = uncompressedBuffer.toByteArray();
            data = ByteBuffer.wrap(uncompressedBytes);
        }
        catch (IOException e)
        {
            // TODO - shouldn't happen
            throw new AMQException("Error uncompressing gzipped message data", e);
        }
        return data;
    }


    public AbstractJMSMessage createMessage(long messageNbr, boolean redelivered, ContentHeaderBody contentHeader,
                                            AMQShortString exchange, AMQShortString routingKey, List bodies,
                                                         AMQSession_0_8.DestinationCache<AMQQueue> queueDestinationCache,
                                                         AMQSession_0_8.DestinationCache<AMQTopic> topicDestinationCache)
            throws JMSException, AMQException
    {
        final AbstractJMSMessage msg = create08MessageWithBody(messageNbr, contentHeader, exchange, routingKey, bodies, queueDestinationCache, topicDestinationCache);
        msg.setJMSRedelivered(redelivered);
        msg.setReceivedFromServer();
        return msg;
    }

    public AbstractJMSMessage createMessage(long messageNbr, boolean redelivered, MessageProperties msgProps,
                                            DeliveryProperties deliveryProps, java.nio.ByteBuffer body)
            throws JMSException, AMQException
    {
        final AbstractJMSMessage msg =
                create010MessageWithBody(messageNbr,msgProps,deliveryProps, body);
        msg.setJMSRedelivered(redelivered);
        msg.setReceivedFromServer();
        return msg;
    }

    private class BodyInputStream extends InputStream
    {
        private final Iterator<ContentBody> _bodiesIter;
        private byte[] _currentBuffer;
        private int _currentPos;
        public BodyInputStream(final List<ContentBody> bodies)
        {
            _bodiesIter = bodies.iterator();
            _currentBuffer = _bodiesIter.next().getPayload();
            _currentPos = 0;
        }

        @Override
        public int read() throws IOException
        {
            byte[] buf = new byte[1];
            int size = read(buf);
            if(size == -1)
            {
                throw new EOFException();
            }
            else
            {
                return ((int)buf[0])&0xff;
            }
        }

        @Override
        public int read(final byte[] dst, final int off, final int len)
        {
            while(_currentPos == _currentBuffer.length)
            {
                if(!_bodiesIter.hasNext())
                {
                    return -1;
                }
                else
                {
                    _currentBuffer = _bodiesIter.next().getPayload();
                    _currentPos = 0;
                }
            }
            int size = Math.min(len, _currentBuffer.length - _currentPos);
            System.arraycopy(_currentBuffer,_currentPos, dst,off,size);
            _currentPos+=size;
            return size;
        }

        @Override
        public void close()
        {
        }
    }
}
