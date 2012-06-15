package org.apache.qpid.messaging.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
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
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageDeliveryMode;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.codec.BBDecoder;
import org.apache.qpid.transport.codec.BBEncoder;
import org.apache.qpid.util.UUIDGen;
import org.apache.qpid.util.UUIDs;

/**
 * A generic message factory that is based on the AMQO 0-10 encoding.
 *
 */
public class MessageFactory_AMQP_0_10 implements MessageFactory
{
    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");
    private static boolean ALLOCATE_DIRECT = Boolean.getBoolean("qpid.allocate-direct");
    private static final ByteBuffer EMPTY_BYTE_BUFFER = ALLOCATE_DIRECT ? ByteBuffer.allocateDirect(0) : ByteBuffer.allocate(0);

    @Override
    public Message createMessage(String text) throws MessageEncodingException
    {
        return new StringMessage_AMQP_0_10(new Mesage_AMQP_0_10(), text);
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
        return new Mesage_AMQP_0_10(b);
    }

    @Override
    public Message createMessage(ByteBuffer buf) throws MessageEncodingException
    {
        if (ALLOCATE_DIRECT)
        {
            if (buf.isDirect())
            {
                return new Mesage_AMQP_0_10(buf);
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
            return new Mesage_AMQP_0_10(buf);
        }
    }

    @Override
    public Message createMessage(Map<String, Object> map) throws MessageEncodingException
    {
        return new MapMessage_AMQP_0_10(new Mesage_AMQP_0_10(), map);
    }

    @Override
    public Message createMessage(List<Object> list) throws MessageEncodingException
    {
        return new ListMessage_AMQP_0_10(new Mesage_AMQP_0_10(), list);
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
            return decodeAsString(m.getContent());
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
            return decodeAsMap(m.getContent());
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
            return decodeAsList(m.getContent());
        }
    }

    class Mesage_AMQP_0_10 implements Message
    {
        private MessageProperties _messageProps;
        private DeliveryProperties _deliveryProps;
        private ByteBuffer _data;

        private UUIDGen _ssnNameGenerator = UUIDs.newGenerator();

        protected Mesage_AMQP_0_10(MessageProperties messageProps, DeliveryProperties deliveryProps)
        {
            this(messageProps, deliveryProps,EMPTY_BYTE_BUFFER);
        }
        protected Mesage_AMQP_0_10(MessageProperties messageProps,
                DeliveryProperties deliveryProps,
                ByteBuffer buf)
        {
            _messageProps = messageProps;
            _deliveryProps = deliveryProps;
            _data = buf;
        }

        protected Mesage_AMQP_0_10()
        {
            _messageProps = new MessageProperties();
            _deliveryProps = new DeliveryProperties();
        }

        protected Mesage_AMQP_0_10(ByteBuffer buf)
        {
            _messageProps = new MessageProperties();
            _deliveryProps = new DeliveryProperties();
            _data = buf;
        }

        @Override
        public String getMessageId() throws MessagingException
        {
            return _messageProps.getMessageId().toString();
        }

        @Override
        public void setMessageId(String messageId) throws MessagingException
        {
            // Temp hack for the time being
            _messageProps.setMessageId(_ssnNameGenerator.generate());
        }

        @Override
        public String getSubject() throws MessagingException
        {
            Map<String,Object> props = _messageProps.getApplicationHeaders();
            return props == null ? null : (String)props.get(QPID_SUBJECT);
        }

        @Override
        public void setSubject(String subject) throws MessagingException
        {
            Map<String,Object> props = _messageProps.getApplicationHeaders();
            if (props == null)
            {
                props = new HashMap<String,Object>();
                _messageProps.setApplicationHeaders(props);
            }
            props.put(QPID_SUBJECT, subject);
        }

        @Override
        public String getContentType() throws MessagingException
        {
            return _messageProps.getContentType();
        }

        @Override
        public void setContentType(String contentType)
        throws MessagingException
        {
            _messageProps.setContentType(contentType);
        }

        @Override
        public String getCorrelationId() throws MessagingException
        {
            return new String(_messageProps.getCorrelationId());
        }

        @Override
        public void setCorrelationId(String correlationId)
        throws MessagingException
        {
            _messageProps.setCorrelationId(correlationId.getBytes());
        }

        @Override
        public String getReplyTo() throws MessagingException
        {
            return addressFrom0_10_ReplyTo(_deliveryProps.getExchange(),
                    _deliveryProps.getRoutingKey());
        }

        @Override
        public void setReplyTo(String replyTo) throws MessagingException
        {
            // TODO
        }

        @Override
        public String getUserId() throws MessagingException
        {
            return new String(_messageProps.getUserId());
        }

        @Override
        public void setUserId(String userId) throws MessagingException
        {
            _messageProps.setUserId(userId.getBytes());
        }

        @Override
        public boolean isDurable() throws MessagingException
        {
            return _deliveryProps.getDeliveryMode() == MessageDeliveryMode.PERSISTENT;
        }

        @Override
        public void setDurable(boolean durable) throws MessagingException
        {
            _deliveryProps.setDeliveryMode(durable ?
                    MessageDeliveryMode.PERSISTENT: MessageDeliveryMode.NON_PERSISTENT);
        }

        @Override
        public boolean isRedelivered() throws MessagingException
        {
            return _deliveryProps.getRedelivered();
        }

        @Override
        public void setRedelivered(boolean redelivered)
        throws MessagingException
        {

            _deliveryProps.setRedelivered(redelivered);
        }

        @Override
        public int getPriority() throws MessagingException
        {
            return _deliveryProps.getPriority().getValue();
        }

        @Override
        public void setPriority(int priority) throws MessagingException
        {
            _deliveryProps.setPriority(MessageDeliveryPriority.get((short)priority));

        }

        @Override
        public long getTtl() throws MessagingException
        {
            return _deliveryProps.getTtl();
        }

        @Override
        public void setTtl(long ttl) throws MessagingException
        {
            _deliveryProps.setTtl(ttl);
        }

        @Override
        public long getTimestamp() throws MessagingException
        {
            return _deliveryProps.getTimestamp();
        }

        @Override
        public void setTimestamp(long timestamp) throws MessagingException
        {
            _deliveryProps.setTimestamp(timestamp);
        }

        @Override
        public Map<String, Object> getProperties() throws MessagingException
        {
            return _messageProps.getApplicationHeaders();
        }

        @Override
        public void setProperty(String key, Object value)
        throws MessagingException
        {
            Map<String,Object> props = _messageProps.getApplicationHeaders();
            if (props == null)
            {
                props = new HashMap<String,Object>();
                _messageProps.setApplicationHeaders(props);
            }
            props.put(key, value);
        }

        private String addressFrom0_10_ReplyTo(String exchange, String routingKey)
        {
            if ("".equals(exchange)) // type Queue
            {
                return routingKey;
            }
            else
            {
                return exchange + "/" + routingKey;
            }
        }

        @Override
        public ByteBuffer getContent()
        {
            return _data;
        }
    }

    class StringMessage_AMQP_0_10 extends GenericMessageAdapter implements StringMessage
    {
        private String _str;
        private ByteBuffer _rawData;
        private MessageEncodingException _exception;

        /**
         * @param data The ByteBuffer passed will be read from position zero.
         */
        public StringMessage_AMQP_0_10(MessageProperties messageProps,
                DeliveryProperties deliveryProps,
                ByteBuffer data)
        {
            super(new Mesage_AMQP_0_10(messageProps, deliveryProps));
            _rawData = (ByteBuffer) data.rewind();
            try
            {
                _str = decodeAsString(_rawData.duplicate());
            }
            catch (MessageEncodingException e)
            {
                _exception = e;
            }
        }

        public StringMessage_AMQP_0_10(Message delegate, String str) throws MessageEncodingException
        {
            super(delegate);
            if(_str == null || _str.isEmpty())
            {
                _rawData = EMPTY_BYTE_BUFFER;
            }
            else
            {
                _rawData = encodeString(str);
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

        @Override
        public ByteBuffer getContent()
        {
            return _rawData;
        }
    }

    /**
     * @param data The ByteBuffer passed will be read from position zero.
     */
    class MapMessage_AMQP_0_10 extends GenericMessageAdapter implements MapMessage
    {
        private Map<String,Object> _map;
        private ByteBuffer _rawData;
        private MessageEncodingException _exception;

        public MapMessage_AMQP_0_10(MessageProperties messageProps,
                DeliveryProperties deliveryProps,
                ByteBuffer data)
        {
            super(new Mesage_AMQP_0_10(messageProps, deliveryProps));
            _rawData = (ByteBuffer) data.rewind();
            try
            {
                _map = decodeAsMap(_rawData.duplicate());
            }
            catch (MessageEncodingException e)
            {
                _exception = e;
            }
        }

        public MapMessage_AMQP_0_10(Message delegate, Map<String,Object> map) throws MessageEncodingException
        {
            super(delegate);
            if(map == null || map.isEmpty())
            {
                _rawData = EMPTY_BYTE_BUFFER;
            }
            else
            {
                _rawData = encodeMap(map);
            }
        }

        @Override
        public Map<String,Object> getMap() throws MessageEncodingException
        {
            if (_exception != null)
            {
                throw _exception;
            }
            else
            {
                return _map;
            }
        }

        @Override
        public ByteBuffer getContent()
        {
            return _rawData;
        }
    }

    /**
     * @param data The ByteBuffer passed will be read from position zero.
     */
    class ListMessage_AMQP_0_10 extends GenericMessageAdapter implements ListMessage
    {
        private List<Object> _list;
        private ByteBuffer _rawData;
        private MessageEncodingException _exception;

        public ListMessage_AMQP_0_10(MessageProperties messageProps,
                DeliveryProperties deliveryProps,
                ByteBuffer data)
        {
            super(new Mesage_AMQP_0_10(messageProps, deliveryProps));
            _rawData = (ByteBuffer) data.rewind();
            try
            {
                _list = decodeAsList(_rawData.duplicate());
            }
            catch (MessageEncodingException e)
            {
                _exception = e;
            }
        }

        public ListMessage_AMQP_0_10(Message delegate, List<Object> list) throws MessageEncodingException
        {
            super(delegate);
            if(list == null || list.isEmpty())
            {
                _rawData = EMPTY_BYTE_BUFFER;
            }
            else
            {
                _rawData = encodeList(list);
            }
        }

        @Override
        public List<Object> getList() throws MessageEncodingException
        {
            if (_exception != null)
            {
                throw _exception;
            }
            else
            {
                return _list;
            }
        }

        @Override
        public ByteBuffer getContent()
        {
            return _rawData;
        }
    }

    protected static String decodeAsString(ByteBuffer buf) throws MessageEncodingException
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

    protected static ByteBuffer encodeString(String str) throws MessageEncodingException
    {
        final CharsetEncoder encoder = DEFAULT_CHARSET.newEncoder();
        ByteBuffer b;
        try
        {
            b = encoder.encode(CharBuffer.wrap(str));
            b.flip();
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

    protected static Map<String,Object> decodeAsMap(ByteBuffer buf) throws MessageEncodingException
    {
        try
        {
            BBDecoder decorder = new BBDecoder();
            decorder.init(buf);
            return decorder.readMap();
        }
        catch (Exception e)
        {
            throw new MessageEncodingException("Error decoding content as Map",e);
        }
    }

    protected static ByteBuffer encodeMap(Map<String,Object> map) throws MessageEncodingException
    {
        try
        {
            //need to investigate the capacity here.
            BBEncoder encoder = new BBEncoder(1024);
            encoder.writeMap(map);
            return (ByteBuffer)encoder.buffer().flip();
        }
        catch (Exception e)
        {
            throw new MessageEncodingException("Cannot encode Map" ,e);
        }
    }

    protected static List<Object> decodeAsList(ByteBuffer buf) throws MessageEncodingException
    {
        try
        {
            BBDecoder decorder = new BBDecoder();
            decorder.init(buf);
            return decorder.readList();
        }
        catch (Exception e)
        {
            throw new MessageEncodingException("Error decoding content as List",e);
        }
    }

    protected static ByteBuffer encodeList(List<Object> list) throws MessageEncodingException
    {
        try
        {
            //need to investigate the capacity here.
            BBEncoder encoder = new BBEncoder(1024);
            encoder.writeList(list);
            return (ByteBuffer)encoder.buffer().flip();
        }
        catch (Exception e)
        {
            throw new MessageEncodingException("Cannot encode List" ,e);
        }
    }
}
