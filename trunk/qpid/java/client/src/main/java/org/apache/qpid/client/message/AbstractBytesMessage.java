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

import java.io.IOException;
import java.nio.charset.Charset;

import javax.jms.JMSException;
import javax.jms.MessageEOFException;

import org.apache.mina.common.ByteBuffer;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;

/**
 * @author Apache Software Foundation
 */
public abstract class AbstractBytesMessage extends AbstractJMSMessage
{

    /**
     * The default initial size of the buffer. The buffer expands automatically.
     */
    private static final int DEFAULT_BUFFER_INITIAL_SIZE = 1024;

    AbstractBytesMessage(AMQMessageDelegateFactory delegateFactory)
    {
        this(delegateFactory, null);
    }

    /**
     * Construct a bytes message with existing data.
     *
     * @param delegateFactory
     * @param data the data that comprises this message. If data is null, you get a 1024 byte buffer that is
     */
    AbstractBytesMessage(AMQMessageDelegateFactory delegateFactory, ByteBuffer data)
    {
        super(delegateFactory, data); // this instanties a content header
        setContentType(getMimeType());

        if (_data == null)
        {
            allocateInitialBuffer();
        }
    }

    protected void allocateInitialBuffer()
    {
        _data = ByteBuffer.allocate(DEFAULT_BUFFER_INITIAL_SIZE);
        _data.setAutoExpand(true);
    }

    AbstractBytesMessage(AMQMessageDelegate delegate, ByteBuffer data) throws AMQException
     {
         super(delegate, data);
         setContentType(getMimeType());
     }


    public void clearBodyImpl() throws JMSException
    {
        allocateInitialBuffer();
    }

    public String toBodyString() throws JMSException
    {
        checkReadable();
        try
        {
            return getText();
        }
        catch (IOException e)
        {
            JMSException jmse = new JMSException(e.toString());
            jmse.setLinkedException(e);
            throw jmse;
        }
    }

    /**
     * We reset the stream before and after reading the data. This means that toString() will always output
     * the entire message and also that the caller can then immediately start reading as if toString() had
     * never been called.
     *
     * @return
     * @throws IOException
     */
    private String getText() throws IOException
    {
        // this will use the default platform encoding
        if (_data == null)
        {
            return null;
        }

        int pos = _data.position();
        _data.rewind();
        // one byte left is for the end of frame marker
        if (_data.remaining() == 0)
        {
            // this is really redundant since pos must be zero
            _data.position(pos);

            return null;
        }
        else
        {
            String data = _data.getString(Charset.forName("UTF8").newDecoder());
            _data.position(pos);

            return data;
        }
    }

    /**
     * Check that there is at least a certain number of bytes available to read
     *
     * @param len the number of bytes
     * @throws javax.jms.MessageEOFException if there are less than len bytes available to read
     */
    protected void checkAvailable(int len) throws MessageEOFException
    {
        if (_data.remaining() < len)
        {
            throw new MessageEOFException("Unable to read " + len + " bytes");
        }
    }
}
