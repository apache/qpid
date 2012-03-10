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
package org.apache.qpid.server.message;

import org.apache.qpid.AMQException;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

public class MessageProtocolConversionTest extends QpidBrokerTestCase
{

    private static final int TIMEOUT = 1500;
    private Connection _connection_0_9_1;
    private Connection _connection_0_10;

    private static final boolean BOOLEAN_TEST_VAL = true;
    private static final byte BYTE_TEST_VAL = (byte) 4;
    private static final byte[] BYTES_TEST_VAL = {5, 4, 3, 2, 1};
    private static final char CHAR_TEST_VAL = 'x';
    private static final double DOUBLE_TEST_VAL = Double.MAX_VALUE;
    private static final float FLOAT_TEST_VAL = Float.MAX_VALUE;
    private static final int INT_TEST_VAL = -73;
    private static final long LONG_TEST_VAL = Long.MIN_VALUE / 2l;
    private static final short SHORT_TEST_VAL = -586;
    private static final String STRING_TEST_VAL = "This is a test text message";

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        setTestSystemProperty(ClientProperties.AMQP_VERSION, "0-10");
        _connection_0_10 = getConnection();
        setTestSystemProperty(ClientProperties.AMQP_VERSION, "0-9-1");
        _connection_0_9_1 = getConnection();
    }

    public void test0_9_1_to_0_10_conversion() throws JMSException, AMQException
    {
        doConversionTests(_connection_0_9_1, _connection_0_10);
    }

    public void test_0_10_to_0_9_1_conversion() throws JMSException, AMQException
    {

        doConversionTests(_connection_0_10, _connection_0_9_1);
    }

    private void doConversionTests(Connection producerConn, Connection consumerConn) throws JMSException, AMQException
    {
        Session producerSession = producerConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Session consumerSession = consumerConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue queue = getTestQueue();

        MessageProducer producer = producerSession.createProducer(queue);
        MessageConsumer consumer = consumerSession.createConsumer(queue);

        consumerConn.start();
        producerConn.start();

        // Text Message

        Message m = producerSession.createTextMessage(STRING_TEST_VAL);
        producer.send(m);
        m = consumer.receive(TIMEOUT);

        assertNotNull("Expected text message did not arrive", m);
        assertTrue("Received message not an instance of TextMessage (" + m.getClass().getName() + " instead)", m instanceof TextMessage);
        assertEquals("Message text not as expected", STRING_TEST_VAL, ((TextMessage) m).getText());

        // Map Message

        MapMessage mapMessage = producerSession.createMapMessage();
        mapMessage.setBoolean("boolean", BOOLEAN_TEST_VAL);
        mapMessage.setByte("byte", BYTE_TEST_VAL);
        mapMessage.setBytes("bytes", BYTES_TEST_VAL);
        mapMessage.setChar("char", CHAR_TEST_VAL);
        mapMessage.setDouble("double", DOUBLE_TEST_VAL);
        mapMessage.setFloat("float", FLOAT_TEST_VAL);
        mapMessage.setInt("int", INT_TEST_VAL);
        mapMessage.setLong("long", LONG_TEST_VAL);
        mapMessage.setShort("short", SHORT_TEST_VAL);
        mapMessage.setString("string", STRING_TEST_VAL);

        producer.send(mapMessage);

        m = consumer.receive(TIMEOUT);

        assertNotNull("Expected map message message did not arrive", m);
        assertTrue("Received message not an instance of MapMessage (" + m.getClass().getName() + " instead)", m instanceof MapMessage);
        MapMessage receivedMapMessage = (MapMessage) m;
        assertEquals("Map message boolean value not as expected", BOOLEAN_TEST_VAL, receivedMapMessage.getBoolean("boolean"));
        assertEquals("Map message byte value not as expected", BYTE_TEST_VAL, receivedMapMessage.getByte("byte"));
        assertTrue("Map message bytes value not as expected", Arrays.equals(BYTES_TEST_VAL, receivedMapMessage.getBytes("bytes")));
        assertEquals("Map message char value not as expected", CHAR_TEST_VAL, receivedMapMessage.getChar("char"));
        assertEquals("Map message double value not as expected", DOUBLE_TEST_VAL, receivedMapMessage.getDouble("double"));
        assertEquals("Map message float value not as expected", FLOAT_TEST_VAL, receivedMapMessage.getFloat("float"));
        assertEquals("Map message int value not as expected", INT_TEST_VAL, receivedMapMessage.getInt("int"));
        assertEquals("Map message long value not as expected", LONG_TEST_VAL, receivedMapMessage.getLong("long"));
        assertEquals("Map message short value not as expected", SHORT_TEST_VAL, receivedMapMessage.getShort("short"));
        assertEquals("Map message string value not as expected", STRING_TEST_VAL, receivedMapMessage.getString("string"));
        ArrayList expectedNames = Collections.list(mapMessage.getMapNames());        
        Collections.sort(expectedNames);
        ArrayList actualNames = Collections.list(receivedMapMessage.getMapNames());
        Collections.sort(actualNames);
        assertEquals("Map message keys not as expected", expectedNames, actualNames);
        
        // Stream Message

        StreamMessage streamMessage = producerSession.createStreamMessage();
        streamMessage.writeString(STRING_TEST_VAL);
        streamMessage.writeShort(SHORT_TEST_VAL);
        streamMessage.writeLong(LONG_TEST_VAL);
        streamMessage.writeInt(INT_TEST_VAL);
        streamMessage.writeFloat(FLOAT_TEST_VAL);
        streamMessage.writeDouble(DOUBLE_TEST_VAL);
        streamMessage.writeChar(CHAR_TEST_VAL);
        streamMessage.writeBytes(BYTES_TEST_VAL);
        streamMessage.writeByte(BYTE_TEST_VAL);
        streamMessage.writeBoolean(BOOLEAN_TEST_VAL);

        producer.send(streamMessage);
        
        m = consumer.receive(TIMEOUT);
        
        assertNotNull("Expected stream message message did not arrive", m);
        assertTrue("Received message not an instance of StreamMessage (" + m.getClass().getName() + " instead)", m instanceof StreamMessage);
        StreamMessage receivedStreamMessage = (StreamMessage) m;        
        
        assertEquals("Stream message read string not as expected", STRING_TEST_VAL, receivedStreamMessage.readString());
        assertEquals("Stream message read short not as expected", SHORT_TEST_VAL, receivedStreamMessage.readShort());
        assertEquals("Stream message read long not as expected", LONG_TEST_VAL, receivedStreamMessage.readLong());
        assertEquals("Stream message read int not as expected", INT_TEST_VAL, receivedStreamMessage.readInt());
        assertEquals("Stream message read float not as expected", FLOAT_TEST_VAL, receivedStreamMessage.readFloat());
        assertEquals("Stream message read double not as expected", DOUBLE_TEST_VAL, receivedStreamMessage.readDouble());
        assertEquals("Stream message read char not as expected", CHAR_TEST_VAL, receivedStreamMessage.readChar());
        byte[] bytesVal = new byte[BYTES_TEST_VAL.length];
        receivedStreamMessage.readBytes(bytesVal);
        assertTrue("Stream message read bytes not as expected", Arrays.equals(BYTES_TEST_VAL, bytesVal));
        assertEquals("Stream message read byte not as expected", BYTE_TEST_VAL, receivedStreamMessage.readByte());
        assertEquals("Stream message read boolean not as expected", BOOLEAN_TEST_VAL, receivedStreamMessage.readBoolean());

        try
        {
            receivedStreamMessage.readByte();
            fail("Unexpected remaining bytes in stream message");
        }
        catch(MessageEOFException e)
        {
            // pass
        }

        // Object Message

        ObjectMessage objectMessage = producerSession.createObjectMessage();
        objectMessage.setObject(STRING_TEST_VAL);

        producer.send(objectMessage);

        m = consumer.receive(TIMEOUT);

        assertNotNull("Expected object message message did not arrive", m);
        assertTrue("Received message not an instance of ObjectMessage (" + m.getClass().getName() + " instead)", m instanceof ObjectMessage);
        ObjectMessage receivedObjectMessage = (ObjectMessage) m;
        assertEquals("Object message value not as expected", STRING_TEST_VAL, receivedObjectMessage.getObject());


        // Bytes Message

        BytesMessage bytesMessage = producerSession.createBytesMessage();
        bytesMessage.writeBytes(BYTES_TEST_VAL);

        producer.send(bytesMessage);

        m = consumer.receive(TIMEOUT);

        assertNotNull("Expected bytes message message did not arrive", m);
        assertTrue("Received message not an instance of BytesMessage (" + m.getClass().getName() + " instead)", m instanceof BytesMessage);
        BytesMessage receivedBytesMessage = (BytesMessage) m;
        bytesVal = new byte[BYTES_TEST_VAL.length];
        receivedBytesMessage.readBytes(bytesVal);
        assertTrue("Bytes message read bytes not as expected", Arrays.equals(BYTES_TEST_VAL, bytesVal));

        try
        {
            receivedBytesMessage.readByte();
            fail("Unexpected remaining bytes in stream message");
        }
        catch(MessageEOFException e)
        {
            // pass
        }

        // Headers / properties tests
        
        Message msg = producerSession.createMessage();
        msg.setJMSCorrelationID("testCorrelationId");
        msg.setJMSDeliveryMode(DeliveryMode.NON_PERSISTENT);
        msg.setJMSPriority(7);
        msg.setJMSType("testType");

        msg.setBooleanProperty("boolean", BOOLEAN_TEST_VAL);
        msg.setByteProperty("byte", BYTE_TEST_VAL);
        msg.setDoubleProperty("double", DOUBLE_TEST_VAL);
        msg.setFloatProperty("float", FLOAT_TEST_VAL);
        msg.setIntProperty("int", INT_TEST_VAL);
        msg.setLongProperty("long", LONG_TEST_VAL);
        msg.setShortProperty("short", SHORT_TEST_VAL);
        msg.setStringProperty("string", STRING_TEST_VAL);
        
        producer.send(msg);

        m = consumer.receive(TIMEOUT);
        assertNotNull("Expected message did not arrive", m);
        assertEquals("JMSMessageID differs", msg.getJMSMessageID(), m.getJMSMessageID());
        assertEquals("JMSCorrelationID differs",msg.getJMSCorrelationID(),m.getJMSCorrelationID());
        assertEquals("JMSDeliveryMode differs",msg.getJMSDeliveryMode(),m.getJMSDeliveryMode());
        assertEquals("JMSPriority differs",msg.getJMSPriority(),m.getJMSPriority());
        assertEquals("JMSType differs",msg.getJMSType(),m.getJMSType());

        assertEquals("Message boolean property not as expected", BOOLEAN_TEST_VAL, m.getBooleanProperty("boolean"));
        assertEquals("Message byte property not as expected", BYTE_TEST_VAL, m.getByteProperty("byte"));
        assertEquals("Message double property not as expected", DOUBLE_TEST_VAL, m.getDoubleProperty("double"));
        assertEquals("Message float property not as expected", FLOAT_TEST_VAL, m.getFloatProperty("float"));
        assertEquals("Message int property not as expected", INT_TEST_VAL, m.getIntProperty("int"));
        assertEquals("Message long property not as expected", LONG_TEST_VAL, m.getLongProperty("long"));
        assertEquals("Message short property not as expected", SHORT_TEST_VAL, m.getShortProperty("short"));
        assertEquals("Message string property not as expected", STRING_TEST_VAL, m.getStringProperty("string"));

        ArrayList<String> sentPropNames = Collections.list(msg.getPropertyNames());
        Collections.sort(sentPropNames);
        ArrayList<String> receivedPropNames = Collections.list(m.getPropertyNames());
        Collections.sort(receivedPropNames);

        // Shouldn't really need to do this, the client should be hiding these from us
        removeSyntheticProperties(sentPropNames);
        removeSyntheticProperties(receivedPropNames);

        assertEquals("Property names were not as expected", sentPropNames, receivedPropNames);

        // Test Reply To Queue

        Destination replyToDestination = producerSession.createTemporaryQueue();
        MessageConsumer replyToConsumer = producerSession.createConsumer(replyToDestination);
        msg = producerSession.createMessage();
        msg.setJMSReplyTo(replyToDestination);
        producer.send(msg);

        m = consumer.receive(TIMEOUT);
        assertNotNull("Expected message did not arrive", m);
        assertNotNull("Message does not have ReplyTo set", m.getJMSReplyTo());

        MessageProducer responseProducer = consumerSession.createProducer(m.getJMSReplyTo());
        responseProducer.send(consumerSession.createMessage());

        assertNotNull("Expected response message did not arrive", replyToConsumer.receive(TIMEOUT));

        // Test Reply To Topic

        replyToDestination = producerSession.createTemporaryTopic();
        replyToConsumer = producerSession.createConsumer(replyToDestination);
        msg = producerSession.createMessage();
        msg.setJMSReplyTo(replyToDestination);
        producer.send(msg);

        m = consumer.receive(TIMEOUT);
        assertNotNull("Expected message did not arrive", m);
        assertNotNull("Message does not have ReplyTo set", m.getJMSReplyTo());

        responseProducer = consumerSession.createProducer(m.getJMSReplyTo());
        responseProducer.send(consumerSession.createMessage());

        assertNotNull("Expected response message did not arrive", replyToConsumer.receive(TIMEOUT));


    }

    private void removeSyntheticProperties(ArrayList<String> propNames)
    {
        Iterator<String> nameIter = propNames.iterator();
        while(nameIter.hasNext())
        {
            String propName = nameIter.next();
            if(propName.startsWith("x-jms") || propName.startsWith("JMS_QPID"))
            {
                nameIter.remove();
            }
        }
    }

    @Override
    public void tearDown() throws Exception
    {
        _connection_0_9_1.close();
        _connection_0_10.close();
        super.tearDown();
    }
}
