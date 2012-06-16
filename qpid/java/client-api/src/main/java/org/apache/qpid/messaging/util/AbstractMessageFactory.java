/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.messaging.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.messaging.ListMessage;
import org.apache.qpid.messaging.MapMessage;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessageEncodingException;
import org.apache.qpid.messaging.MessageFactory;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.StringMessage;

/**
 * A generic message factory that has abstract methods for
 * protocol or implementation specific message delegates and codecs.
 *
 * @see MessageFactory_AMQP_0_10 @see CppMessageFactory
 */
public abstract class AbstractMessageFactory implements MessageFactory
{
    protected static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");
    protected static boolean ALLOCATE_DIRECT = Boolean.getBoolean("qpid.allocate-direct");
    protected static final ByteBuffer EMPTY_BYTE_BUFFER = ALLOCATE_DIRECT ? ByteBuffer.allocateDirect(0) : ByteBuffer.allocate(0);

    protected final Map<String, MessageType> _contentTypeToMsgTypeMap = new HashMap<String, MessageType>();

    protected AbstractMessageFactory()
    {
        _contentTypeToMsgTypeMap.put("text/plain", MessageType.STRING);
        _contentTypeToMsgTypeMap.put("text/xml", MessageType.STRING);
        _contentTypeToMsgTypeMap.put("amqp/map", MessageType.MAP);
        _contentTypeToMsgTypeMap.put("amqp-0-10/map", MessageType.MAP);
        _contentTypeToMsgTypeMap.put("amqp/list", MessageType.LIST);
        _contentTypeToMsgTypeMap.put("amqp-0-10/list", MessageType.LIST);
        _contentTypeToMsgTypeMap.put("application/octet-stream", MessageType.BINARY);
    }

    // ----------- Methods for public API --------------------------
    @Override
    public Message createMessage(String text) throws MessageEncodingException
    {
        return createStringMessage(createFactorySpecificMessageDelegate(), text);
    }

    @Override
    public Message createMessage(byte[] bytes) throws MessageEncodingException
    {
        ByteBuffer b;
        if (ALLOCATE_DIRECT)
        {
            b = ByteBuffer.allocateDirect(bytes.length);
            b.put(bytes);
        }
        else
        {
            b = ByteBuffer.wrap(bytes);
        }
        return createMessage(b);
    }

    @Override
    public Message createMessage(ByteBuffer buf) throws MessageEncodingException
    {
        if (ALLOCATE_DIRECT)
        {
            if (buf.isDirect())
            {
                return createMessage(buf);
            }
            else
            {
                // Silently copying the data to a direct buffer is not a good thing as it can
                // add a perf overhead. So an exception is a more reasonable option.
                throw new MessageEncodingException("The ByteBuffer needs to be direct allocated");
            }
        }
        else
        {
            return createMessage(buf);
        }
    }

    @Override
    public Message createMessage(Map<String, Object> map) throws MessageEncodingException
    {
        return createMapMessage(createFactorySpecificMessageDelegate(), map);
    }

    @Override
    public Message createMessage(List<Object> list) throws MessageEncodingException
    {
        return createListMessage(createFactorySpecificMessageDelegate(), list);
    }

    @Override
    public String getContentAsString(Message m) throws MessageEncodingException
    {
        if (m instanceof StringMessage)
        {
            return ((StringMessage)m).getString();
        }
        else
        {
            try
            {
                return decodeAsString(m.getContent());
            }
            catch (MessagingException e)
            {
                throw new MessageEncodingException("Unable to access content",e);
            }
        }
    }

    @Override
    public Map<String, Object> getContentAsMap(Message m) throws MessageEncodingException
    {
        if (m instanceof MapMessage)
        {
            return ((MapMessage)m).getMap();
        }
        else
        {
            try
            {
                return decodeAsMap(m.getContent());
            }
            catch (MessagingException e)
            {
                throw new MessageEncodingException("Unable to access content",e);
            }
        }
    }

    @Override
    public List<Object> getContentAsList(Message m) throws MessageEncodingException
    {
        if (m instanceof ListMessage)
        {
            return ((ListMessage)m).getList();
        }
        else
        {
            try
            {
                return decodeAsList(m.getContent());
            }
            catch (MessagingException e)
            {
                throw new MessageEncodingException("Unable to access content",e);
            }
        }
    }

    public void registerContentType(String contentType, MessageType type)
    {
        _contentTypeToMsgTypeMap.put(contentType, type);
    }

    // ----------- Methods for internal API --------------------------

    protected abstract Message createFactorySpecificMessageDelegate();

    protected abstract Class<? extends MessageFactory> getFactoryClass();

    protected abstract Map<String,Object> decodeAsMap(ByteBuffer buf) throws MessageEncodingException;

    protected abstract ByteBuffer encodeMap(Map<String,Object> map) throws MessageEncodingException;

    protected abstract List<Object> decodeAsList(ByteBuffer buf) throws MessageEncodingException;

    protected abstract ByteBuffer encodeList(List<Object> list) throws MessageEncodingException;

    protected Message makeMessageReadOnly(Message m)
    {
        return new ReadOnlyMessageAdapter(m);
    }

    protected Message createDefaultMessage(Message delegate, ByteBuffer data)
    {
        return new DefaultMessageImpl(delegate, data);
    }

    protected StringMessage createStringMessage(Message delegate, String str) throws MessageEncodingException
    {
        return new StringMessageImpl(delegate, str);
    }

    protected StringMessage createStringMessage(Message delegate, ByteBuffer buf)
    {
        return new StringMessageImpl(delegate, buf);
    }

    protected MapMessage createMapMessage(Message delegate, Map<String, Object> map) throws MessageEncodingException
    {
        return new MapMessageImpl(delegate, map);
    }

    protected MapMessage createMapMessage(Message delegate, ByteBuffer buf)
    {
        return new MapMessageImpl(delegate, buf);
    }

    protected ListMessage createListMessage(Message delegate, List<Object> list) throws MessageEncodingException
    {
        return new ListMessageImpl(delegate, list);
    }

    protected ListMessage createListMessage(Message delegate, ByteBuffer buf) throws MessageEncodingException
    {
        return new ListMessageImpl(delegate, buf);
    }

    protected Message createMessage(Message delegate, ByteBuffer buf) throws MessagingException
    {
        MessageType type = _contentTypeToMsgTypeMap.containsKey(delegate.getContentType())?
                _contentTypeToMsgTypeMap.get(delegate.getContentType()) : MessageType.BINARY;

        switch (type)
        {
            case STRING:
                return createStringMessage(delegate,buf);

            case MAP:
                return createMapMessage(delegate,buf);

            case LIST:
                return createListMessage(delegate,buf);

            default:
                return createDefaultMessage(delegate,buf);
        }
    }

    protected class DefaultMessageImpl extends GenericMessageAdapter
    {
        protected ByteBuffer _rawData;

        public DefaultMessageImpl(Message delegate, ByteBuffer data)
        {
            super(delegate);
            _rawData = (ByteBuffer) data.rewind();
        }

        public DefaultMessageImpl(Message delegate)
        {
            super(delegate);
            _rawData = EMPTY_BYTE_BUFFER;
        }

        @Override
        public ByteBuffer getContent()
        {
            return _rawData;
        }

        @Override
        public Class<? extends MessageFactory> getMessageFactoryClass()
        {
            return getFactoryClass();
        }

        @Override
        public Object getFactorySpecificMessageDelegate()
        {
            return super.getDelegate();
        }

        @Override
        public String toString()
        {
            return super.getDelegate().toString();
        }
    }

    protected class StringMessageImpl extends DefaultMessageImpl implements StringMessage
    {
        private String _str;
        private MessageEncodingException _exception;

        /**
         * @param data The ByteBuffer passed will be read from position zero.
         */
        public StringMessageImpl(Message delegate, ByteBuffer data)
        {
            super(delegate, data);
            try
            {
                _str = decodeAsString(_rawData.duplicate());
            }
            catch (MessageEncodingException e)
            {
                _exception = e;
            }
        }

        public StringMessageImpl(Message delegate, String str) throws MessageEncodingException
        {
            super(delegate);
            _str = str;
            if(str != null && !str.isEmpty())
            {
                _rawData = encodeString(str);
                setContentTypeIfNotSet(delegate,"text/plain");
            }
        }

        @Override
        public String getString() throws MessageEncodingException
        {
            if (_exception != null)
            {
                throw _exception;
            }
            else
            {
                return _str;
            }
        }
    }

    /**
     * @param data The ByteBuffer passed will be read from position zero.
     */
    protected class MapMessageImpl extends DefaultMessageImpl implements MapMessage
    {
        private Map<String,Object> _map;
        private MessageEncodingException _exception;

        public MapMessageImpl(Message delegate, ByteBuffer data)
        {
            super(delegate, data);
            try
            {
                _map = decodeAsMap(_rawData.duplicate());
            }
            catch (MessageEncodingException e)
            {
                _exception = e;
            }
        }

        public MapMessageImpl(Message delegate, Map<String,Object> map) throws MessageEncodingException
        {
            super(delegate);
            _map = map;
            if(map != null && !map.isEmpty())
            {
                _rawData = encodeMap(map);
                setContentTypeIfNotSet(delegate,"amqp/map");
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Map<String,Object> getMap() throws MessageEncodingException
        {
            if (_exception != null)
            {
                throw _exception;
            }
            else
            {
                return _map == null ? Collections.EMPTY_MAP : _map;
            }
        }
    }

    /**
     * @param data The ByteBuffer passed will be read from position zero.
     */
    protected class ListMessageImpl extends DefaultMessageImpl implements ListMessage
    {
        private List<Object> _list;
        private MessageEncodingException _exception;

        public ListMessageImpl(Message delegate, ByteBuffer data)
        {
            super(delegate, data);
            try
            {
                _list = decodeAsList(_rawData.duplicate());
            }
            catch (MessageEncodingException e)
            {
                _exception = e;
            }
        }

        public ListMessageImpl(Message delegate, List<Object> list) throws MessageEncodingException
        {
            super(delegate);
            _list = list;
            if(list != null && !list.isEmpty())
            {
                _rawData = encodeList(list);
                setContentTypeIfNotSet(delegate,"amqp/list");
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public List<Object> getList() throws MessageEncodingException
        {
            if (_exception != null)
            {
                throw _exception;
            }
            else
            {
                return _list == null ? Collections.EMPTY_LIST : _list;
            }
        }
    }

    protected String decodeAsString(ByteBuffer buf) throws MessageEncodingException
    {
        final CharsetDecoder decoder = DEFAULT_CHARSET.newDecoder();
        try
        {
            return decoder.decode(buf).toString();
        }
        catch (CharacterCodingException e)
        {
            throw new MessageEncodingException("Error decoding content as String using UTF-8",e);
        }

    }

    protected ByteBuffer encodeString(String str) throws MessageEncodingException
    {
        final CharsetEncoder encoder = DEFAULT_CHARSET.newEncoder();
        ByteBuffer b;
        try
        {
            b = encoder.encode(CharBuffer.wrap(str));
        }
        catch (CharacterCodingException e)
        {
            throw new MessageEncodingException("Cannot encode string in UFT-8: " + str,e);
        }
        if (ALLOCATE_DIRECT)
        {
            // unfortunately this extra copy is required as it does not seem possible
            // to create a CharSetEncoder that returns a buffer allocated directly.
            ByteBuffer direct = ByteBuffer.allocateDirect(b.remaining());
            direct.put(b);
            direct.flip();
            return direct;
        }
        else
        {
            return b;
        }
    }

    protected void setContentTypeIfNotSet(Message m, String contentType)
    {
        try
        {
            if (m.getContentType() == null || m.getContentType().isEmpty())
            {
                m.setContentType(contentType);
            }
        }
        catch (MessagingException e)
        {
            //ignore.
        }
    }
}
