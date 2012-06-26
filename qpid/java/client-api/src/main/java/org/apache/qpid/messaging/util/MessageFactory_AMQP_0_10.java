package org.apache.qpid.messaging.util;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessageEncodingException;
import org.apache.qpid.messaging.MessageFactory;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageDeliveryMode;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.codec.BBDecoder;
import org.apache.qpid.transport.codec.BBEncoder;
import org.apache.qpid.util.UUIDGen;
import org.apache.qpid.util.UUIDs;

/**
 * A generic message factory that is based on the AMQP 0-10 encoding.
 *
 */
public class MessageFactory_AMQP_0_10 extends AbstractMessageFactory
{
    protected Class<? extends MessageFactory> getFactoryClass()
    {
        return MessageFactory_AMQP_0_10.class;
    }

    protected Map<String,Object> decodeAsMap(ByteBuffer buf) throws MessageEncodingException
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

    protected ByteBuffer encodeMap(Map<String,Object> map) throws MessageEncodingException
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

    protected List<Object> decodeAsList(ByteBuffer buf) throws MessageEncodingException
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

    protected ByteBuffer encodeList(List<Object> list) throws MessageEncodingException
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

    @Override
    protected Message createFactorySpecificMessageDelegate()
    {
        return new Mesage_AMQP_0_10_Delegate();
    }

    class Mesage_AMQP_0_10_Delegate implements Message
    {
        private MessageProperties _messageProps;
        private DeliveryProperties _deliveryProps;

        private UUIDGen _ssnNameGenerator = UUIDs.newGenerator();

        // creating a new message for sending
        protected Mesage_AMQP_0_10_Delegate()
        {
            this(new MessageProperties(),new DeliveryProperties());
        }

        // Message received with data.
        protected Mesage_AMQP_0_10_Delegate(MessageProperties messageProps,
                                            DeliveryProperties deliveryProps)
        {
            _messageProps = messageProps;
            _deliveryProps = deliveryProps;
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

        // noop, this is for the headers only.
        @Override
        public ByteBuffer getContent() throws MessagingException
        {
            throw new MessagingException("Empty!");
        }
    }
}
