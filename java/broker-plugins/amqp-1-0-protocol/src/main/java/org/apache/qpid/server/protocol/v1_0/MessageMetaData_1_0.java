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
package org.apache.qpid.server.protocol.v1_0;

import java.nio.ByteBuffer;
import java.util.*;

import org.apache.log4j.Logger;
import org.apache.qpid.amqp_1_0.codec.ValueHandler;
import org.apache.qpid.amqp_1_0.messaging.SectionDecoder;
import org.apache.qpid.amqp_1_0.messaging.SectionEncoder;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpSequence;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.Data;
import org.apache.qpid.amqp_1_0.type.messaging.DeliveryAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Footer;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.MessageAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;

public class MessageMetaData_1_0 implements StorableMessageMetaData
{
    private static final Logger _logger = Logger.getLogger(MessageMetaData_1_0.class);
    // TODO move to somewhere more useful
    public static final Symbol JMS_TYPE = Symbol.valueOf("x-opt-jms-type");
    public static final MessageMetaDataType.Factory<MessageMetaData_1_0> FACTORY = new MetaDataFactory();
    private static final MessageMetaDataType_1_0 TYPE = new MessageMetaDataType_1_0();


    private Header _header;
    private Properties _properties;
    private Map _deliveryAnnotations;
    private Map _messageAnnotations;
    private Map _appProperties;
    private Map _footer;

    private List<ByteBuffer> _encodedSections = new ArrayList<ByteBuffer>(3);

    private volatile ByteBuffer _encoded;
    private MessageHeader_1_0 _messageHeader;


    public MessageMetaData_1_0(List<Section> sections, SectionEncoder encoder)
    {
        this(sections, encodeSections(sections, encoder));
    }

    public Properties getPropertiesSection()
    {
        return _properties;
    }


    public Header getHeaderSection()
    {
        return _header;
    }

    private static ArrayList<ByteBuffer> encodeSections(final List<Section> sections, final SectionEncoder encoder)
    {
        ArrayList<ByteBuffer> encodedSections = new ArrayList<ByteBuffer>(sections.size());
        for(Section section : sections)
        {
            encoder.encodeObject(section);
            encodedSections.add(encoder.getEncoding().asByteBuffer());
            encoder.reset();
        }
        return encodedSections;
    }

    public MessageMetaData_1_0(ByteBuffer[] fragments, SectionDecoder decoder)
    {
        this(fragments, decoder, new ArrayList<ByteBuffer>(3));
    }

    public MessageMetaData_1_0(ByteBuffer[] fragments, SectionDecoder decoder, List<ByteBuffer> immutableSections)
    {
        this(constructSections(fragments, decoder,immutableSections), immutableSections);
    }

    private MessageMetaData_1_0(List<Section> sections, List<ByteBuffer> encodedSections)
    {
        _encodedSections = encodedSections;

        Iterator<Section> sectIter = sections.iterator();

        Section section = sectIter.hasNext() ? sectIter.next() : null;
        if(section instanceof Header)
        {
            _header = (Header) section;
            section = sectIter.hasNext() ? sectIter.next() : null;
        }

        if(section instanceof DeliveryAnnotations)
        {
            _deliveryAnnotations = ((DeliveryAnnotations) section).getValue();
            section = sectIter.hasNext() ? sectIter.next() : null;
        }

        if(section instanceof MessageAnnotations)
        {
            _messageAnnotations = ((MessageAnnotations) section).getValue();
            section = sectIter.hasNext() ? sectIter.next() : null;
        }

        if(section instanceof Properties)
        {
            _properties = (Properties) section;
            section = sectIter.hasNext() ? sectIter.next() : null;
        }

        if(section instanceof ApplicationProperties)
        {
            _appProperties = ((ApplicationProperties) section).getValue();
            section = sectIter.hasNext() ? sectIter.next() : null;
        }

        if(section instanceof Footer)
        {
            _footer = ((Footer) section).getValue();
            section = sectIter.hasNext() ? sectIter.next() : null;
        }

        _messageHeader = new MessageHeader_1_0();

    }

    private static List<Section> constructSections(final ByteBuffer[] fragments, final SectionDecoder decoder, List<ByteBuffer> encodedSections)
    {
        List<Section> sections = new ArrayList<Section>(3);

        ByteBuffer src;
        if(fragments.length == 1)
        {
            src = fragments[0].duplicate();
        }
        else
        {
            int size = 0;
            for(ByteBuffer buf : fragments)
            {
                size += buf.remaining();
            }
            src = ByteBuffer.allocate(size);
            for(ByteBuffer buf : fragments)
            {
                src.put(buf.duplicate());
            }
            src.flip();

        }

        try
        {
            int startBarePos = -1;
            int lastPos = src.position();
            Section s = decoder.readSection(src);



            if(s instanceof Header)
            {
                sections.add(s);
                lastPos = src.position();
                s = src.hasRemaining() ? decoder.readSection(src) : null;
            }

            if(s instanceof DeliveryAnnotations)
            {
                sections.add(s);
                lastPos = src.position();
                s = src.hasRemaining() ? decoder.readSection(src) : null;
            }

            if(s instanceof MessageAnnotations)
            {
                sections.add(s);
                lastPos = src.position();
                s = src.hasRemaining() ? decoder.readSection(src) : null;
            }

            if(s instanceof Properties)
            {
                sections.add(s);
                if(startBarePos == -1)
                {
                    startBarePos = lastPos;
                }
                s = src.hasRemaining() ? decoder.readSection(src) : null;
            }

            if(s instanceof ApplicationProperties)
            {
                sections.add(s);
                if(startBarePos == -1)
                {
                    startBarePos = lastPos;
                }
                s = src.hasRemaining() ? decoder.readSection(src) : null;
            }

            if(s instanceof AmqpValue)
            {
                if(startBarePos == -1)
                {
                    startBarePos = lastPos;
                }
                s = src.hasRemaining() ? decoder.readSection(src) : null;
            }
            else if(s instanceof Data)
            {
                if(startBarePos == -1)
                {
                    startBarePos = lastPos;
                }
                do
                {
                    s = src.hasRemaining() ? decoder.readSection(src) : null;
                } while(s instanceof Data);
            }
            else if(s instanceof AmqpSequence)
            {
                if(startBarePos == -1)
                {
                    startBarePos = lastPos;
                }
                do
                {
                    s = src.hasRemaining() ? decoder.readSection(src) : null;
                }
                while(s instanceof AmqpSequence);
            }

            if(s instanceof Footer)
            {
                sections.add(s);
            }


            int pos = 0;
            for(ByteBuffer buf : fragments)
            {
/*
                if(pos < startBarePos)
                {
                    if(pos + buf.remaining() > startBarePos)
                    {
                        ByteBuffer dup = buf.duplicate();
                        dup.position(dup.position()+startBarePos-pos);
                        dup.slice();
                        encodedSections.add(dup);
                    }
                }
                else
*/
                {
                    encodedSections.add(buf.duplicate());
                }
                pos += buf.remaining();
            }

            return sections;
        }
        catch (AmqpErrorException e)
        {
            _logger.error("Decoding read section error", e);
            throw new IllegalArgumentException(e);
        }
    }


    public MessageMetaDataType getType()
    {
        return TYPE;
    }


    public int getStorableSize()
    {
        int size = 0;

        for(ByteBuffer bin : _encodedSections)
        {
            size += bin.limit();
        }

        return size;
    }

    private ByteBuffer encodeAsBuffer()
    {
        ByteBuffer buf = ByteBuffer.allocate(getStorableSize());

        for(ByteBuffer bin : _encodedSections)
        {
            buf.put(bin.duplicate());
        }

        return buf;
    }

    public int writeToBuffer(ByteBuffer dest)
    {
        ByteBuffer buf = _encoded;

        if(buf == null)
        {
            buf = encodeAsBuffer();
            _encoded = buf;
        }

        buf = buf.duplicate();

        buf.position(0);

        if(dest.remaining() < buf.limit())
        {
            buf.limit(dest.remaining());
        }
        dest.put(buf);
        return buf.limit();
    }

    public int getContentSize()
    {
        ByteBuffer buf = _encoded;

        if(buf == null)
        {
            buf = encodeAsBuffer();
            _encoded = buf;
        }
        return buf.remaining();
    }

    public boolean isPersistent()
    {
        return _header != null && Boolean.TRUE.equals(_header.getDurable());
    }

    public MessageHeader_1_0 getMessageHeader()
    {
        return _messageHeader;
    }




    private static class MetaDataFactory implements MessageMetaDataType.Factory<MessageMetaData_1_0>
    {
        private final AMQPDescribedTypeRegistry _typeRegistry = AMQPDescribedTypeRegistry.newInstance();

        private MetaDataFactory()
        {
            _typeRegistry.registerTransportLayer();
            _typeRegistry.registerMessagingLayer();
            _typeRegistry.registerTransactionLayer();
            _typeRegistry.registerSecurityLayer();
        }

        public MessageMetaData_1_0 createMetaData(ByteBuffer buf)
        {
            ValueHandler valueHandler = new ValueHandler(_typeRegistry);

            ArrayList<Section> sections = new ArrayList<Section>(3);
            ArrayList<ByteBuffer> encodedSections = new ArrayList<ByteBuffer>(3);

            while(buf.hasRemaining())
            {
                try
                {
                    ByteBuffer encodedBuf = buf.duplicate();
                    Object parse = valueHandler.parse(buf);
                    sections.add((Section) parse);
                    encodedBuf.limit(buf.position());
                    encodedSections.add(encodedBuf);

                }
                catch (AmqpErrorException e)
                {
                    //TODO
                    throw new ConnectionScopedRuntimeException(e);
                }

            }

            return new MessageMetaData_1_0(sections,encodedSections);

        }
    }

    public class MessageHeader_1_0 implements AMQMessageHeader
    {

        public String getCorrelationId()
        {
            if(_properties == null || _properties.getCorrelationId() == null)
            {
                return null;
            }
            else
            {
                return _properties.getCorrelationId().toString();
            }
        }

        public long getExpiration()
        {
            return 0;  //TODO
        }

        public String getMessageId()
        {
            if(_properties == null || _properties.getMessageId() == null)
            {
                return null;
            }
            else
            {
                return _properties.getMessageId().toString();
            }
        }

        public String getMimeType()
        {

            if(_properties == null || _properties.getContentType() == null)
            {
                return null;
            }
            else
            {
                return _properties.getContentType().toString();
            }
        }

        public String getEncoding()
        {
            return null;  //TODO
        }

        public byte getPriority()
        {
            if(_header == null || _header.getPriority() == null)
            {
                return 4; //javax.jms.Message.DEFAULT_PRIORITY;
            }
            else
            {
                return _header.getPriority().byteValue();
            }
        }

        public long getTimestamp()
        {
            if(_properties == null || _properties.getCreationTime() == null)
            {
                return 0L;
            }
            else
            {
                return _properties.getCreationTime().getTime();
            }

        }

        public String getType()
        {

            if(_messageAnnotations == null || _messageAnnotations.get(JMS_TYPE) == null)
            {
                return null;
            }
            else
            {
                return _messageAnnotations.get(JMS_TYPE).toString();
            }
        }

        public String getReplyTo()
        {
            if(_properties == null || _properties.getReplyTo() == null)
            {
                return null;
            }
            else
            {
                return _properties.getReplyTo().toString();
            }
        }

        public String getAppId()
        {
            //TODO
            return null;
        }

        public String getUserId()
        {
            // TODO
            return null;
        }

        public Object getHeader(final String name)
        {
            return _appProperties == null ? null : _appProperties.get(name);
        }

        public boolean containsHeaders(final Set<String> names)
        {
            if(_appProperties == null)
            {
                return false;
            }

            for(String key : names)
            {
                if(!_appProperties.containsKey(key))
                {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Collection<String> getHeaderNames()
        {
            if(_appProperties == null)
            {
                return Collections.emptySet();
            }
            return Collections.unmodifiableCollection(_appProperties.keySet());
        }

        public boolean containsHeader(final String name)
        {
            return _appProperties != null && _appProperties.containsKey(name);
        }

        public String getSubject()
        {
            return _properties == null ? null : _properties.getSubject();
        }

        public String getTo()
        {
            return _properties == null ? null : _properties.getTo();
        }

        public Map<String, Object> getHeadersAsMap()
        {
            return new HashMap<String, Object>(_appProperties);
        }
    }

}
