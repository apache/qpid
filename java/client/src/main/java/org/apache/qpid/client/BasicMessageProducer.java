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
package org.apache.qpid.client;

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.JMSBytesMessage;
import org.apache.qpid.client.message.MessageHeaders;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.framing.*;

import javax.jms.*;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class BasicMessageProducer extends Closeable implements org.apache.qpid.jms.MessageProducer
{
    protected final Logger _logger = Logger.getLogger(getClass());

    private AMQConnection _connection;

    /**
     * If true, messages will not get a timestamp.
     */
    private boolean _disableTimestamps;

    /**
     * Priority of messages created by this producer.
     */
    private int _messagePriority;

    /**
     * Time to live of messages. Specified in milliseconds but AMQ has 1 second resolution.
     */
    private long _timeToLive;

    /**
     * Delivery mode used for this producer.
     */
    private int _deliveryMode = DeliveryMode.PERSISTENT;

    /**
     * The Destination used for this consumer, if specified upon creation.
     */
    protected AMQDestination _destination;

    /**
     * Default encoding used for messages produced by this producer.
     */
    private String _encoding;

    /**
     * Default encoding used for message produced by this producer.
     */
    private String _mimeType;

    private AMQProtocolHandler _protocolHandler;

    /**
     * True if this producer was created from a transacted session
     */
    private boolean _transacted;

    private int _channelId;

    /**
     * This is an id generated by the session and is used to tie individual producers to the session. This means we
     * can deregister a producer with the session when the producer is clsoed. We need to be able to tie producers
     * to the session so that when an error is propagated to the session it can close the producer (meaning that
     * a client that happens to hold onto a producer reference will get an error if he tries to use it subsequently).
     */
    private long _producerId;

    /**
     * The session used to create this producer
     */
    private AMQSession _session;

    private final boolean _immediate;

    private final boolean _mandatory;

    private final boolean _waitUntilSent;
    private static final Content[] NO_CONTENT = new Content[0];

    protected BasicMessageProducer(AMQConnection connection, AMQDestination destination, boolean transacted,
                                   int channelId, AMQSession session, AMQProtocolHandler protocolHandler,
                                   long producerId, boolean immediate, boolean mandatory, boolean waitUntilSent)
                                   throws AMQException
    {
        _connection = connection;
        _destination = destination;
        _transacted = transacted;
        _protocolHandler = protocolHandler;
        _channelId = channelId;
        _session = session;
        _producerId = producerId;
        if (destination != null)
        {
            declareDestination(destination);
        }
        _immediate = immediate;
        _mandatory = mandatory;
        _waitUntilSent = waitUntilSent;
    }

    void resubscribe() throws AMQException
    {
        if (_destination != null)
        {
            declareDestination(_destination);
        }
    }

    private void declareDestination(AMQDestination destination) throws AMQException
    {
        // Declare the exchange
        // Note that the durable and internal arguments are ignored since passive is set to false
        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        AMQMethodBody methodBody = ExchangeDeclareBody.createMethodBody(
            (byte)0, (byte)9,	// AMQP version (major, minor)
            null,	// arguments
            false,	// autoDelete
            false,	// durable
            destination.getExchangeName(),	// exchange
            false,	// internal
            true,	// nowait
            false,	// passive
            0,	// ticket
            destination.getExchangeClass());	// type
        _protocolHandler.writeRequest(_channelId, methodBody);
    }

    public void setDisableMessageID(boolean b) throws JMSException
    {
        checkPreConditions();
        checkNotClosed();
        // IGNORED
    }

    public boolean getDisableMessageID() throws JMSException
    {
        checkNotClosed();
        // Always false for AMQP
        return false;
    }

    public void setDisableMessageTimestamp(boolean b) throws JMSException
    {
        checkPreConditions();
        _disableTimestamps = b;
    }

    public boolean getDisableMessageTimestamp() throws JMSException
    {
        checkNotClosed();
        return _disableTimestamps;
    }

    public void setDeliveryMode(int i) throws JMSException
    {
        checkPreConditions();
        if (i != DeliveryMode.NON_PERSISTENT && i != DeliveryMode.PERSISTENT)
        {
            throw new JMSException("DeliveryMode must be either NON_PERSISTENT or PERSISTENT. Value of " + i +
                    " is illegal");
        }
        _deliveryMode = i;
    }

    public int getDeliveryMode() throws JMSException
    {
        checkNotClosed();
        return _deliveryMode;
    }

    public void setPriority(int i) throws JMSException
    {
        checkPreConditions();
        if (i < 0 || i > 9)
        {
            throw new IllegalArgumentException("Priority of " + i + " is illegal. Value must be in range 0 to 9");
        }
        _messagePriority = i;
    }

    public int getPriority() throws JMSException
    {
        checkNotClosed();
        return _messagePriority;
    }

    public void setTimeToLive(long l) throws JMSException
    {
        checkPreConditions();
        if (l < 0)
        {
            throw new IllegalArgumentException("Time to live must be non-negative - supplied value was " + l);
        }
        _timeToLive = l;
    }

    public long getTimeToLive() throws JMSException
    {
        checkNotClosed();
        return _timeToLive;
    }

    public Destination getDestination() throws JMSException
    {
        checkNotClosed();
        return _destination;
    }

    public void close() throws JMSException
    {
        _closed.set(true);
        _session.deregisterProducer(_producerId);
    }

    public void send(Message message) throws JMSException
    {
        checkPreConditions();
        checkInitialDestination();


        synchronized (_connection.getFailoverMutex())
        {
            sendImpl(_destination, message, _deliveryMode, _messagePriority, _timeToLive,
                     _mandatory, _immediate);
        }
    }

    public void send(Message message, int deliveryMode) throws JMSException
    {
        checkPreConditions();
        checkInitialDestination();

        synchronized (_connection.getFailoverMutex())
        {
            sendImpl(_destination, message, deliveryMode, _messagePriority, _timeToLive,
                     _mandatory, _immediate);
        }
    }

    public void send(Message message, int deliveryMode, boolean immediate) throws JMSException
    {
        checkPreConditions();
        checkInitialDestination();
        synchronized (_connection.getFailoverMutex())
        {
            sendImpl(_destination, message, deliveryMode, _messagePriority, _timeToLive,
                     _mandatory, immediate);
        }
    }

    public void send(Message message, int deliveryMode, int priority,
                     long timeToLive) throws JMSException
    {
        checkPreConditions();
        checkInitialDestination();
        synchronized (_connection.getFailoverMutex())
        {
            sendImpl(_destination, message, deliveryMode, priority, timeToLive, _mandatory,
                     _immediate);
        }
    }

    public void send(Destination destination, Message message) throws JMSException
    {
        checkPreConditions();
        checkDestination(destination);
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, message, _deliveryMode, _messagePriority, _timeToLive,
                     _mandatory, _immediate);
        }
    }

    public void send(Destination destination, Message message, int deliveryMode,
                     int priority, long timeToLive)
            throws JMSException
    {
        checkPreConditions();
        checkDestination(destination);
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, message, deliveryMode, priority, timeToLive,
                     _mandatory, _immediate);
        }
    }

    public void send(Destination destination, Message message, int deliveryMode,
                     int priority, long timeToLive, boolean mandatory)
            throws JMSException
    {
        checkPreConditions();
        checkDestination(destination);
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, message, deliveryMode, priority, timeToLive,
                     mandatory, _immediate);
        }
    }

    public void send(Destination destination, Message message, int deliveryMode,
                     int priority, long timeToLive, boolean mandatory, boolean immediate)
            throws JMSException
    {
        checkPreConditions();
        checkDestination(destination);
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, message, deliveryMode, priority, timeToLive,
                     mandatory, immediate);
        }
    }

    public void send(Destination destination, Message message, int deliveryMode,
                     int priority, long timeToLive, boolean mandatory,
                     boolean immediate, boolean waitUntilSent)
            throws JMSException, AMQException
    {
        checkPreConditions();
        checkDestination(destination);
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, message, deliveryMode, priority, timeToLive,
                     mandatory, immediate, waitUntilSent);
        }
    }


    private AbstractJMSMessage convertToNativeMessage(Message message) throws JMSException
    {
        if (message instanceof AbstractJMSMessage)
        {
            return (AbstractJMSMessage) message;
        }
        else
        {
            AbstractJMSMessage newMessage;

            if (message instanceof BytesMessage)
            {
                BytesMessage bytesMessage = (BytesMessage) message;
                bytesMessage.reset();

                JMSBytesMessage nativeMsg = (JMSBytesMessage) _session.createBytesMessage();


                byte[] buf = new byte[1024];

                int len;

                while ((len = bytesMessage.readBytes(buf)) != -1)
                {
                    nativeMsg.writeBytes(buf, 0, len);
                }

                newMessage = nativeMsg;
            }
            else if (message instanceof MapMessage)
            {
                MapMessage origMessage = (MapMessage) message;
                MapMessage nativeMessage = _session.createMapMessage();

                Enumeration mapNames = origMessage.getMapNames();
                while (mapNames.hasMoreElements())
                {
                    String name = (String) mapNames.nextElement();
                    nativeMessage.setObject(name, origMessage.getObject(name));
                }
                newMessage = (AbstractJMSMessage) nativeMessage;
            }
            else if (message instanceof ObjectMessage)
            {
                ObjectMessage origMessage = (ObjectMessage) message;
                ObjectMessage nativeMessage = _session.createObjectMessage();

                nativeMessage.setObject(origMessage.getObject());

                newMessage = (AbstractJMSMessage) nativeMessage;
            }
            else if (message instanceof TextMessage)
            {
                TextMessage origMessage = (TextMessage) message;
                TextMessage nativeMessage = _session.createTextMessage();

                nativeMessage.setText(origMessage.getText());

                newMessage = (AbstractJMSMessage) nativeMessage;
            }
            else if (message instanceof StreamMessage)
            {
                StreamMessage origMessage = (StreamMessage) message;
                StreamMessage nativeMessage = _session.createStreamMessage();


                try
                {
                    origMessage.reset();
                    while (true)
                    {
                        nativeMessage.writeObject(origMessage.readObject());
                    }
                }
                catch (MessageEOFException e)
                {
                    ;//
                }
                newMessage = (AbstractJMSMessage) nativeMessage;
            }
            else
            {
                newMessage = (AbstractJMSMessage) _session.createMessage();

            }

            Enumeration propertyNames = message.getPropertyNames();
            while (propertyNames.hasMoreElements())
            {
                String propertyName = String.valueOf(propertyNames.nextElement());
                if (!propertyName.startsWith("JMSX_"))
                {
                    Object value = message.getObjectProperty(propertyName);
                    newMessage.setObjectProperty(propertyName, value);
                }
            }

            newMessage.setJMSDeliveryMode(message.getJMSDeliveryMode());


            int priority = message.getJMSPriority();
            if (priority < 0)
            {
                priority = 0;
            }
            else if (priority > 9)
            {
                priority = 9;
            }

            newMessage.setJMSPriority(priority);
            if (message.getJMSReplyTo() != null)
            {
                newMessage.setJMSReplyTo(message.getJMSReplyTo());
            }
            newMessage.setJMSType(message.getJMSType());


            if (newMessage != null)
            {
                return newMessage;
            }
            else
            {
                throw new JMSException("Unable to send message, due to class conversion error: " + message.getClass().getName());
            }
        }
    }


    private void validateDestination(Destination destination) throws JMSException
    {
        if (!(destination instanceof AMQDestination))
        {
            throw new JMSException("Unsupported destination class: " +
                    (destination != null ? destination.getClass() : null));
        }
        try
        {
            declareDestination((AMQDestination) destination);
        }
        catch (AMQException e)
        {
            throw new JMSException(e.toString());
        }
    }

    protected void sendImpl(AMQDestination destination, Message message, int deliveryMode, int priority,
                            long timeToLive, boolean mandatory, boolean immediate) throws JMSException
    {
        sendImpl(destination, message, deliveryMode, priority, timeToLive, mandatory, immediate, _waitUntilSent);
    }

    /**
     * The caller of this method must hold the failover mutex.
     *
     * @param destination
     * @param origMessage
     * @param deliveryMode
     * @param priority
     * @param timeToLive
     * @param mandatory
     * @param immediate
     * @throws JMSException
     */
    protected void sendImpl(AMQDestination destination, Message origMessage, int deliveryMode, int priority,
                            long timeToLive, boolean mandatory, boolean immediate, boolean wait)
                            throws JMSException
    {
        checkTemporaryDestination(destination);
        origMessage.setJMSDestination(destination);
        
        AbstractJMSMessage message = convertToNativeMessage(origMessage);
        message.getMessageHeaders().getJMSHeaders().setString(CustomJMXProperty.JMSX_QPID_JMSDESTINATIONURL.toString(), destination.toURL());

        long currentTime = 0;
        if (!_disableTimestamps)
        {
            currentTime = System.currentTimeMillis();
            message.setJMSTimestamp(currentTime);
        }
        message.prepareForSending();
        ByteBuffer payload = message.getData();
        MessageHeaders messageHeaders = message.getMessageHeaders();

        if (timeToLive > 0)
        {
            if (!_disableTimestamps)
            {
                messageHeaders.setExpiration(currentTime + timeToLive);
            }
        }
        else
        {
            if (!_disableTimestamps)
            {
                messageHeaders.setExpiration(0);
            }
        }

        int size = (payload != null) ? payload.limit() : 0;
        final long framePayloadMax = _session.getAMQConnection().getMaximumFrameSize();
        
        if (size < framePayloadMax){
        	// Inline message case
        	_logger.debug("Inline case, sending data inline with the transfer method");
        	Content data = new Content(Content.ContentTypeEnum.CONTENT_TYPE_INLINE,payload.array()); 
        	doMessageTransfer(messageHeaders,destination,data,message,deliveryMode,priority,timeToLive,immediate);
        } else {
        	// Reference message case
            // Sequence is as follows 
        	// 1. Message.open
        	// 2. Message.Transfer
        	// 3. "n" of Message.append
        	// 4. Message.close
        	List content = createContent(payload);
        	if(_logger.isDebugEnabled())
        	{
        		_logger.debug("Reference case, sending data as chunks");
        		_logger.debug("Sending " + content.size() + " Message.Transfer frames to " + destination);
        	}
        	// Message.Open
        	String referenceId = generateReferenceId();
        	doMessageOpen(referenceId);
        	
        	// Message.Transfer
        	Content data = new Content(Content.ContentTypeEnum.CONTENT_TYPE_REFERENCE,referenceId.getBytes()); 
        	doMessageTransfer(messageHeaders,destination,data,message,deliveryMode,priority,timeToLive,immediate);
        	
        	//Message.Append
        	for(Iterator it = content.iterator(); it.hasNext();){
        		doMessageAppend(referenceId,(byte[])it.next());
        	}
        	
        	//Message.Close
        	doMessageClose(referenceId);
        }
                
        if (message != origMessage)
        {
            _logger.warn("Updating original message");
            origMessage.setJMSPriority(message.getJMSPriority());
            origMessage.setJMSTimestamp(message.getJMSTimestamp());
            _logger.warn("Setting JMSExpiration:" + message.getJMSExpiration());
            origMessage.setJMSExpiration(message.getJMSExpiration());
            origMessage.setJMSMessageID(message.getJMSMessageID());
        }
    }
    
    private void doMessageTransfer(MessageHeaders messageHeaders,AMQDestination destination, Content content, AbstractJMSMessage message, int deliveryMode, int priority,
            long timeToLive, boolean immediate)throws JMSException{
    	try
        {
            AMQMethodBody methodBody = MessageTransferBody.createMethodBody(
                (byte)0, (byte)9,               // AMQP version (major, minor)
                messageHeaders.getAppId(),      // String appId
                messageHeaders.getJMSHeaders(), // FieldTable applicationHeaders
                content,                     // Content body
                messageHeaders.getEncoding(),   // String contentEncoding
                messageHeaders.getContentType(), // String contentType
                messageHeaders.getCorrelationId(), // String correlationId
                (short)deliveryMode,            // short deliveryMode
                messageHeaders.getDestination(), // String destination
                destination.getExchangeName(),  // String exchange
                messageHeaders.getExpiration(), // long expiration
                immediate,                      // boolean immediate
                messageHeaders.getMessageId(),  // String messageId
                (short)priority,                // short priority
                message.getJMSRedelivered(),    // boolean redelivered
                messageHeaders.getReplyTo(),    // String replyTo
                destination.getRoutingKey(),    // String routingKey
                new String("abc123").getBytes(), // byte[] securityToken
                0,                              // int ticket
                messageHeaders.getTimestamp(),  // long timestamp
                messageHeaders.getTransactionId(), // String transactionId
                timeToLive,                     // long ttl
                messageHeaders.getUserId());    // String userId
    
            _protocolHandler.writeRequest(_channelId, methodBody);
        }
        catch (AMQException e)
        {
            throw new JMSException(e.toString());
        }
    }
    
    private void doMessageOpen(String referenceId){
    	AMQMethodBody methodBody = MessageOpenBody.createMethodBody((byte)0, (byte)9, referenceId.getBytes());
    }
    
    private void doMessageAppend(String referenceId,byte[] data){
    	AMQMethodBody methodBody = MessageAppendBody.createMethodBody((byte)0, (byte)9, data, referenceId.getBytes());
    }
    
    private void doMessageClose(String referenceId){
    	AMQMethodBody methodBody = MessageCloseBody.createMethodBody((byte)0, (byte)9, referenceId.getBytes());
    }
    
    private String generateReferenceId(){
    	return String.valueOf(System.currentTimeMillis());
    }

    private void checkTemporaryDestination(AMQDestination destination) throws JMSException
    {
        if(destination instanceof TemporaryDestination)
        {
            _logger.debug("destination is temporary destination");
            TemporaryDestination tempDest = (TemporaryDestination) destination;
            if(tempDest.getSession().isClosed())
            {
                _logger.debug("session is closed");
                throw new JMSException("Session for temporary destination has been closed");
            }
            if(tempDest.isDeleted())
            {
                _logger.debug("destination is deleted");
                throw new JMSException("Cannot send to a deleted temporary destination");
            }
        }
    }
       
    /**
     * Create content bodies. This will split a large message into numerous bodies depending on the negotiated
     * maximum frame size.
     *
     * @param payload
     * @return the array of content bodies
     */
    private List createContent(ByteBuffer payload)
    {
        int dataLength = payload.remaining();
        final long framePayloadMax = _session.getAMQConnection().getMaximumFrameSize();
        int lastFrame = (dataLength % framePayloadMax) > 0 ? 1 : 0;
        int frameCount = (int) (dataLength / framePayloadMax) + lastFrame;
        List bodies = new LinkedList();

        long remaining = dataLength;
        for (int i = 0; i < frameCount + lastFrame; i++)
        {
            payload.position((int) framePayloadMax * i);
            int length = (remaining >= framePayloadMax) ? (int) framePayloadMax : (int) remaining;
            payload.limit(payload.position() + length);
            bodies.add(payload.slice().array());
            remaining -= length;
        }
        return bodies;
    }

    public void setMimeType(String mimeType) throws JMSException
    {
        checkNotClosed();
        _mimeType = mimeType;
    }

    public void setEncoding(String encoding) throws JMSException, UnsupportedEncodingException
    {
        checkNotClosed();
        _encoding = encoding;
    }

    private void checkPreConditions() throws javax.jms.IllegalStateException, JMSException
    {
        checkNotClosed();

        if (_session == null || _session.isClosed())
        {
            throw new javax.jms.IllegalStateException("Invalid Session");
        }
    }

    private void checkInitialDestination()
    {
        if (_destination == null)
        {
            throw new UnsupportedOperationException("Destination is null");
        }
    }

    private void checkDestination(Destination suppliedDestination) throws InvalidDestinationException
    {
        if (_destination != null && suppliedDestination != null)
        {
            throw new UnsupportedOperationException("This message producer was created with a Destination, therefore you cannot use an unidentified Destination");
        }

        if (suppliedDestination == null)
        {
            throw new InvalidDestinationException("Supplied Destination was invalid");
        }


    }


    public AMQSession getSession()
    {
        return _session;
    }
}
