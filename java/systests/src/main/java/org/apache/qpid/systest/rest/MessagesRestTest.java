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
package org.apache.qpid.systest.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.map.JsonMappingException;

public class MessagesRestTest extends QpidRestTestCase
{

    /**
     * Message number to publish into queue
     */
    private static final int MESSAGE_NUMBER = 12;

    private Connection _connection;
    private Session _session;
    private MessageProducer _producer;
    private long _startTime;
    private long _ttl;

    public void setUp() throws Exception
    {
        super.setUp();
        _startTime = System.currentTimeMillis();
        _connection = getConnection();
        _session = _connection.createSession(true, Session.SESSION_TRANSACTED);
        String queueName = getTestQueueName();
        Destination queue = _session.createQueue(queueName);
        _session.createConsumer(queue).close();
        _producer = _session.createProducer(queue);

        _ttl = TimeUnit.DAYS.toMillis(1);
        for (int i = 0; i < MESSAGE_NUMBER; i++)
        {
            Message m = _session.createTextMessage("Test-" + i);
            m.setIntProperty("index", i);
            if (i % 2 == 0)
            {
                _producer.send(m);
            }
            else
            {
                _producer.send(m, DeliveryMode.NON_PERSISTENT, 5, _ttl);
            }
        }
        _session.commit();
    }

    public void testGet() throws Exception
    {
        String queueName = getTestQueueName();
        List<Map<String, Object>> messages = getRestTestHelper().getJsonAsList("/service/message/test/" + queueName);
        assertNotNull("Messages are not found", messages);
        assertEquals("Unexpected number of messages", MESSAGE_NUMBER, messages.size());
        int position = 0;
        for (Map<String, Object> message : messages)
        {
            assertMessage(position, message);
            position++;
        }
    }

    public void testGetMessageContent() throws Exception
    {
        String queueName = getTestQueueName();

        // add bytes message
        BytesMessage byteMessage = _session.createBytesMessage();
        byte[] messageBytes = "Test".getBytes();
        byteMessage.writeBytes(messageBytes);
        byteMessage.setStringProperty("test", "value");
        _producer.send(byteMessage);
        _session.commit();

        // get message IDs
        List<Long> ids = getMesssageIds(queueName);

        Map<String, Object> message = getRestTestHelper().getJsonAsMap("/service/message/test/" + queueName + "/" + ids.get(0));
        assertMessageAttributes(message);
        assertMessageAttributeValues(message, true);

        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) message.get("headers");
        assertNotNull("Message headers are not found", headers);
        assertEquals("Unexpected message header", 0, headers.get("index"));

        Long lastMessageId = ids.get(ids.size() - 1);
        message = getRestTestHelper().getJsonAsMap("/service/message/test/" + queueName + "/" + lastMessageId);
        assertMessageAttributes(message);
        assertEquals("Unexpected message attribute mimeType", "application/octet-stream", message.get("mimeType"));
        assertEquals("Unexpected message attribute size", 4, message.get("size"));

        @SuppressWarnings("unchecked")
        Map<String, Object> bytesMessageHeader = (Map<String, Object>) message.get("headers");
        assertNotNull("Message headers are not found", bytesMessageHeader);
        assertEquals("Unexpected message header", "value", bytesMessageHeader.get("test"));

        // get content
        byte[] data = getRestTestHelper().getBytes("/service/message-content/test/" + queueName + "/" + lastMessageId);
        assertTrue("Unexpected message", Arrays.equals(messageBytes, data));

    }

    public void testPostMoveMessages() throws Exception
    {
        String queueName = getTestQueueName();
        String queueName2 = queueName + "_2";
        Destination queue2 = _session.createQueue(queueName2);
        _session.createConsumer(queue2);

        // get message IDs
        List<Long> ids = getMesssageIds(queueName);

        // move half of the messages
        int movedNumber = ids.size() / 2;
        List<Long> movedMessageIds = new ArrayList<Long>();
        for (int i = 0; i < movedNumber; i++)
        {
            movedMessageIds.add(ids.remove(i));
        }

        // move messages

        Map<String, Object> messagesData = new HashMap<String, Object>();
        messagesData.put("messages", movedMessageIds);
        messagesData.put("destinationQueue", queueName2);
        messagesData.put("move", Boolean.TRUE);

        getRestTestHelper().submitRequest("/service/message/test/" + queueName, "POST", messagesData, HttpServletResponse.SC_OK);

        // check messages on target queue
        List<Map<String, Object>> messages = getRestTestHelper().getJsonAsList("/service/message/test/" + queueName2);
        assertNotNull("Messages are not found", messages);
        assertEquals("Unexpected number of messages", movedMessageIds.size(), messages.size());
        for (Long id : movedMessageIds)
        {
            Map<String, Object> message = getRestTestHelper().find("id", id.intValue(), messages);
            assertMessageAttributes(message);
        }

        // check messages on original queue
        messages = getRestTestHelper().getJsonAsList("/service/message/test/" + queueName);
        assertNotNull("Messages are not found", messages);
        assertEquals("Unexpected number of messages", ids.size(), messages.size());
        for (Long id : ids)
        {
            Map<String, Object> message = getRestTestHelper().find("id", id.intValue(), messages);
            assertMessageAttributes(message);
        }
        for (Long id : movedMessageIds)
        {
            Map<String, Object> message = getRestTestHelper().find("id", id.intValue(), messages);
            assertNull("Moved message " + id + " is found on original queue", message);
        }
    }

    public void testPostCopyMessages() throws Exception
    {
        String queueName = getTestQueueName();
        String queueName2 = queueName + "_2";
        Destination queue2 = _session.createQueue(queueName2);
        _session.createConsumer(queue2);

        // get message IDs
        List<Long> ids = getMesssageIds(queueName);

        // copy half of the messages
        int copyNumber = ids.size() / 2;
        List<Long> copyMessageIds = new ArrayList<Long>();
        for (int i = 0; i < copyNumber; i++)
        {
            copyMessageIds.add(ids.remove(i));
        }

        // copy messages
        Map<String, Object> messagesData = new HashMap<String, Object>();
        messagesData.put("messages", copyMessageIds);
        messagesData.put("destinationQueue", queueName2);

        getRestTestHelper().submitRequest("/service/message/test/" + queueName, "POST", messagesData, HttpServletResponse.SC_OK);

        // check messages on target queue
        List<Map<String, Object>> messages = getRestTestHelper().getJsonAsList("/service/message/test/" + queueName2);
        assertNotNull("Messages are not found", messages);
        assertEquals("Unexpected number of messages", copyMessageIds.size(), messages.size());
        for (Long id : copyMessageIds)
        {
            Map<String, Object> message = getRestTestHelper().find("id", id.intValue(), messages);
            assertMessageAttributes(message);
        }

        // check messages on original queue
        messages = getRestTestHelper().getJsonAsList("/service/message/test/" + queueName);
        assertNotNull("Messages are not found", messages);
        assertEquals("Unexpected number of messages", MESSAGE_NUMBER, messages.size());
        for (Long id : ids)
        {
            Map<String, Object> message = getRestTestHelper().find("id", id.intValue(), messages);
            assertMessageAttributes(message);
        }
        for (Long id : copyMessageIds)
        {
            Map<String, Object> message = getRestTestHelper().find("id", id.intValue(), messages);
            assertMessageAttributes(message);
        }
    }

    public void testDeleteMessages() throws Exception
    {
        String queueName = getTestQueueName();

        // get message IDs
        List<Long> ids = getMesssageIds(queueName);

        // delete half of the messages
        int deleteNumber = ids.size() / 2;
        StringBuilder queryString = new StringBuilder();
        List<Long> deleteMessageIds = new ArrayList<>();
        for (int i = 0; i < deleteNumber; i++)
        {
            Long id = ids.remove(i);
            deleteMessageIds.add(id);
            if (queryString.length() > 0)
            {
                queryString.append("&");
            }
            queryString.append("id=").append(id);
        }

        // delete messages
        getRestTestHelper().submitRequest("/service/message/test/" + queueName + "?" + queryString.toString(), "DELETE", HttpServletResponse.SC_OK);

        // check messages on queue
        List<Map<String, Object>> messages = getRestTestHelper().getJsonAsList("/service/message/test/" + queueName);
        assertNotNull("Messages are not found", messages);
        assertEquals("Unexpected number of messages", ids.size(), messages.size());
        for (Long id : ids)
        {
            Map<String, Object> message = getRestTestHelper().find("id", id.intValue(), messages);
            assertMessageAttributes(message);
        }
        for (Long id : deleteMessageIds)
        {
            Map<String, Object> message = getRestTestHelper().find("id", id.intValue(), messages);
            assertNull("Message with id " + id + " was not deleted", message);
        }
    }

    public void testClearQueue() throws Exception
    {
        String queueName = getTestQueueName();

        // clear queue
        getRestTestHelper().submitRequest("/service/message/test/" + queueName + "?clear=true", "DELETE", HttpServletResponse.SC_OK);

        // check messages on queue
        List<Map<String, Object>> messages = getRestTestHelper().getJsonAsList("/service/message/test/" + queueName);
        assertNotNull("Messages are not found", messages);
        assertEquals("Unexpected number of messages", 0, messages.size());
    }


    private List<Long> getMesssageIds(String queueName) throws IOException, JsonMappingException
    {
        List<Map<String, Object>> messages = getRestTestHelper().getJsonAsList("/service/message/test/" + queueName);
        List<Long> ids = new ArrayList<Long>();
        for (Map<String, Object> message : messages)
        {
            ids.add(((Number) message.get("id")).longValue());
        }
        return ids;
    }

    private void assertMessage(int position, Map<String, Object> message)
    {
        assertMessageAttributes(message);

        assertEquals("Unexpected message attribute position", position, message.get("position"));
        assertEquals("Unexpected message attribute size", position < 10 ? 6 : 7, message.get("size"));
        boolean even = position % 2 == 0;
        assertMessageAttributeValues(message, even);
    }

    private void assertMessageAttributeValues(Map<String, Object> message, boolean even)
    {
        if (even)
        {
            assertNull("Unexpected message attribute expirationTime", message.get("expirationTime"));
            assertEquals("Unexpected message attribute priority", 4, message.get("priority"));
            assertEquals("Unexpected message attribute persistent", Boolean.TRUE, message.get("persistent"));
        }
        else
        {
            assertEquals("Unexpected message attribute expirationTime", ((Number) message.get("timestamp")).longValue()
                    + _ttl, message.get("expirationTime"));
            assertEquals("Unexpected message attribute priority", 5, message.get("priority"));
            assertEquals("Unexpected message attribute persistent", Boolean.FALSE, message.get("persistent"));
        }
        assertEquals("Unexpected message attribute mimeType", "text/plain", message.get("mimeType"));
        assertEquals("Unexpected message attribute userId", "guest", message.get("userId"));
        assertEquals("Unexpected message attribute deliveryCount", 0, message.get("deliveryCount"));
        assertEquals("Unexpected message attribute state", "Available", message.get("state"));
    }

    private void assertMessageAttributes(Map<String, Object> message)
    {
        assertNotNull("Message map cannot be null", message);
        assertNotNull("Unexpected message attribute deliveryCount", message.get("deliveryCount"));
        assertNotNull("Unexpected message attribute state", message.get("state"));
        assertNotNull("Unexpected message attribute id", message.get("id"));
        assertNotNull("Message arrivalTime cannot be null", message.get("arrivalTime"));
        assertNotNull("Message timestamp cannot be null", message.get("timestamp"));
        assertTrue("Message arrivalTime cannot be null", ((Number) message.get("arrivalTime")).longValue() > _startTime);
        assertNotNull("Message messageId cannot be null", message.get("messageId"));
        assertNotNull("Unexpected message attribute mimeType", message.get("mimeType"));
        assertNotNull("Unexpected message attribute userId", message.get("userId"));
        assertNotNull("Message priority cannot be null", message.get("priority"));
        assertNotNull("Message persistent cannot be null", message.get("persistent"));
    }
}
