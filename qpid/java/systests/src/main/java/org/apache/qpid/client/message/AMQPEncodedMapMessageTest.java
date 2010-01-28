package org.apache.qpid.client.message;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.message.AMQPEncodedMapMessage;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.test.utils.QpidTestCase;


public class AMQPEncodedMapMessageTest extends QpidTestCase
{
    private Connection _connection;
    private Session _session;
    MessageConsumer _consumer;
    MessageProducer _producer;
    
    public void setUp() throws Exception
    {
        super.setUp();
        
        //Create Connection
        _connection = getConnection();
        
        //Create Session
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        //Create Queue
        String queueName = getTestQueueName();
        Queue queue = _session.createQueue(queueName);

        //Create Consumer
        _consumer = _session.createConsumer(queue);

        //Create Producer
        _producer = _session.createProducer(queue);

        _connection.start();
    }

    public void testEmptyMessage() throws JMSException
    {
        MapMessage m = _session.createMapMessage();
        _producer.send(m);
        AMQPEncodedMapMessage msg = (AMQPEncodedMapMessage)_consumer.receive(RECEIVE_TIMEOUT);
        assertNotNull("Message was not received on time",msg);  
        assertEquals("Message content-type is incorrect",
                        AMQPEncodedMapMessage.MIME_TYPE,
                        ((AbstractJMSMessage)msg).getContentType());
        
        assertEquals("Message content should be an empty map",
                Collections.EMPTY_MAP,
                ((AMQPEncodedMapMessage)msg).getMap());
    }
    
    public void testNullMessage() throws JMSException
    {
        MapMessage m = _session.createMapMessage();
        ((AMQPEncodedMapMessage)m).setMap(null);
        _producer.send(m);
        AMQPEncodedMapMessage msg = (AMQPEncodedMapMessage)_consumer.receive(RECEIVE_TIMEOUT);
        assertNotNull("Message was not received on time",msg);  
        assertEquals("Message content-type is incorrect",
                        AMQPEncodedMapMessage.MIME_TYPE,
                        ((AbstractJMSMessage)msg).getContentType());
        
        assertEquals("Message content should be null",
                null,
                ((AMQPEncodedMapMessage)msg).getMap());

    }

    public void testMessageWithContent() throws JMSException
    {
        MapMessage m = _session.createMapMessage();
        m.setBoolean("Boolean", true);
        m.setByte("Byte", (byte)5);
        byte[] bytes = new byte[]{(byte)5,(byte)8};
        m.setBytes("Bytes", bytes);
        m.setChar("Char", 'X');
        m.setDouble("Double", 56.84);
        m.setFloat("Float", Integer.MAX_VALUE + 5000);
        m.setInt("Int", Integer.MAX_VALUE - 5000);
        m.setShort("Short", (short)58);
        m.setString("String", "Hello");            
        _producer.send(m);
        
        AMQPEncodedMapMessage msg = (AMQPEncodedMapMessage)_consumer.receive(RECEIVE_TIMEOUT);
        assertNotNull("Message was not received on time",msg);            
        assertEquals("Message content-type is incorrect",
                        AMQPEncodedMapMessage.MIME_TYPE,
                        ((AbstractJMSMessage)msg).getContentType());
        
        assertEquals(true,m.getBoolean("Boolean"));
        assertEquals((byte)5,m.getByte("Byte"));
        byte[] bytesRcv = m.getBytes("Bytes");
        assertNotNull("Byte array is null",bytesRcv);
        assertEquals((byte)5,bytesRcv[0]);
        assertEquals((byte)8,bytesRcv[1]);
        assertEquals('X',m.getChar("Char"));
        assertEquals(56.84,m.getDouble("Double"));
        //assertEquals(Integer.MAX_VALUE + 5000,m.getFloat("Float"));
        assertEquals(Integer.MAX_VALUE - 5000,m.getInt("Int"));
        assertEquals((short)58,m.getShort("Short"));
        assertEquals("Hello",m.getString("String"));
    }
    
    public void tearDown() throws Exception
    {
        //clean up
        _connection.close();

        super.tearDown();
    }
}
