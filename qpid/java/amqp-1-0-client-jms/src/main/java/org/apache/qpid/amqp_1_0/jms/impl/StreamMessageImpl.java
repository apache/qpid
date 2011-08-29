/*
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
 */

package org.apache.qpid.amqp_1_0.jms.impl;

import org.apache.qpid.amqp_1_0.jms.StreamMessage;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.messaging.*;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

import javax.jms.JMSException;
import java.util.*;

public class StreamMessageImpl extends MessageImpl implements StreamMessage
{
    private List _list;
    private boolean _readOnly;
    private int _position = -1;
    private int _offset = -1;

    protected StreamMessageImpl(Header header, Properties properties, ApplicationProperties appProperties, List list,
                                Footer footer, SessionImpl session)
    {
        super(header, properties, appProperties, footer, session);
        _list = list;
    }

    StreamMessageImpl(final SessionImpl session)
    {
        super(new Header(), new Properties(), new ApplicationProperties(new HashMap()), new Footer(Collections.EMPTY_MAP),
              session);
        _list = new ArrayList();
    }

    public StreamMessageImpl(final Header header,
                             final Properties properties,
                             final ApplicationProperties appProperties,
                             final List amqpListSection, final Footer footer)
    {
        super(header, properties, appProperties, footer, null);
        _list = amqpListSection;
    }

    public boolean readBoolean() throws JMSException
    {
        return false;  //TODO
    }

    public byte readByte() throws JMSException
    {
        return 0;  //TODO
    }

    public short readShort() throws JMSException
    {
        return 0;  //TODO
    }

    public char readChar() throws JMSException
    {
        return 0;  //TODO
    }

    public int readInt() throws JMSException
    {
        return 0;  //TODO
    }

    public long readLong() throws JMSException
    {
        return 0;  //TODO
    }

    public float readFloat() throws JMSException
    {
        return 0;  //TODO
    }

    public double readDouble() throws JMSException
    {
        return 0;  //TODO
    }

    public String readString() throws JMSException
    {
        return null;  //TODO
    }

    public int readBytes(final byte[] bytes) throws JMSException
    {
        return 0;  //TODO
    }

    public Object readObject() throws JMSException
    {
        if(_offset == -1)
        {
            return _list.get(++_position);
        }
        else
        {
            return null;  //TODO
        }
    }

    public void writeBoolean(final boolean b) throws JMSException
    {
        //TODO
    }

    public void writeByte(final byte b) throws JMSException
    {
        _list.add(b);
    }

    public void writeShort(final short i) throws JMSException
    {
        _list.add(i);
    }

    public void writeChar(final char c) throws JMSException
    {
        _list.add(c);
    }

    public void writeInt(final int i) throws JMSException
    {
        _list.add(i);
    }

    public void writeLong(final long l) throws JMSException
    {
        _list.add(l);
    }

    public void writeFloat(final float v) throws JMSException
    {
        _list.add(v);
    }

    public void writeDouble(final double v) throws JMSException
    {
        _list.add(v);
    }

    public void writeString(final String s) throws JMSException
    {
        _list.add(s);
    }

    public void writeBytes(final byte[] bytes) throws JMSException
    {
        //TODO
    }

    public void writeBytes(final byte[] bytes, final int i, final int i1) throws JMSException
    {
        //TODO
    }

    public void writeObject(final Object o) throws JMSException
    {
        //TODO
    }

    public void reset() throws JMSException
    {
        //TODO
    }

    @Override Collection<Section> getSections()
    {
        List<Section> sections = new ArrayList<Section>();
        sections.add(getHeader());
        sections.add(getProperties());
        sections.add(getApplicationProperties());
        sections.add(new AmqpValue(_list));
        sections.add(getFooter());
        return sections;
    }
}
