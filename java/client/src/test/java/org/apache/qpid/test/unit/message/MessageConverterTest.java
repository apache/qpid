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
package org.apache.qpid.test.unit.message;

import junit.framework.TestCase;
import org.apache.qpid.client.message.MessageConverter;
import org.apache.qpid.client.message.JMSTextMessage;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.JMSMapMessage;
import org.apache.qpid.client.AMQQueue;

import javax.jms.Message;
import javax.jms.Destination;
import javax.jms.TextMessage;
import javax.jms.MapMessage;
import java.util.HashMap;


public class MessageConverterTest extends TestCase {

    public static final String JMS_CORR_ID = "QPIDID_01";
    public static final int JMS_DELIV_MODE = 1;
    public static final String JMS_TYPE = "test.jms.type";
    public static final Destination JMS_REPLY_TO = new AMQQueue("my.replyto");

    protected JMSTextMessage testTextMessage;

    protected JMSMapMessage testMapMessage;

    protected void setUp() throws Exception
    {
        super.setUp();
        testTextMessage = new JMSTextMessage();

        //Add JMSProperties
        testTextMessage.setJMSCorrelationID(JMS_CORR_ID);
        testTextMessage.setJMSDeliveryMode(JMS_DELIV_MODE);
        testTextMessage.setJMSType(JMS_TYPE);
        testTextMessage.setJMSReplyTo(JMS_REPLY_TO);
        testTextMessage.setText("testTextMessage text");

        //Add non-JMS properties
        testTextMessage.setStringProperty("testProp1","testValue1");
        testTextMessage.setDoubleProperty("testProp2",Double.MIN_VALUE);

        testMapMessage = new JMSMapMessage();
        testMapMessage.setString("testMapString","testMapStringValue");
        testMapMessage.setDouble("testMapDouble",Double.MAX_VALUE);
    }

    public void testSetProperties() throws Exception
    {
        AbstractJMSMessage newMessage = new MessageConverter((TextMessage)testTextMessage).getConvertedMessage();

        //check JMS prop values on newMessage match
        assertEquals("JMS Correlation ID mismatch",testTextMessage.getJMSCorrelationID(),newMessage.getJMSCorrelationID());
        assertEquals("JMS Delivery mode mismatch",testTextMessage.getJMSDeliveryMode(),newMessage.getJMSDeliveryMode());
        assertEquals("JMS Type mismatch",testTextMessage.getJMSType(),newMessage.getJMSType());
        assertEquals("JMS Reply To mismatch",testTextMessage.getJMSReplyTo(),newMessage.getJMSReplyTo());

        //check non-JMS standard props ok too
        assertEquals("Test String prop value mismatch",testTextMessage.getStringProperty("testProp1"),
                    newMessage.getStringProperty("testProp1"));
        assertEquals("Test Double prop value mismatch",testTextMessage.getDoubleProperty("testProp2"),
                    newMessage.getDoubleProperty("testProp2"));
    }

    public void testJMSTextMessageConversion() throws Exception
    {
        AbstractJMSMessage newMessage = new MessageConverter((TextMessage)testTextMessage).getConvertedMessage();
        assertEquals("Converted message text mismatch",((JMSTextMessage)newMessage).getText(),testTextMessage.getText());
    }

    public void testJMSMapMessageConversion() throws Exception
    {
        AbstractJMSMessage newMessage = new MessageConverter((MapMessage)testMapMessage).getConvertedMessage();
        assertEquals("Converted map message String mismatch",((JMSMapMessage)newMessage).getString("testMapString"),
                    testMapMessage.getString("testMapString"));
        assertEquals("Converted map message Double mismatch",((JMSMapMessage)newMessage).getDouble("testMapDouble"),
                    testMapMessage.getDouble("testMapDouble"));

    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        testTextMessage = null;
    }


}
