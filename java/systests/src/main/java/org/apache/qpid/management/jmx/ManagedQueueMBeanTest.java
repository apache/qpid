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
package org.apache.qpid.management.jmx;

import org.apache.commons.lang.time.FastDateFormat;

import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.server.queue.AMQQueueMBean;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Session;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularData;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Tests the JMX API for the Managed Queue.
 *
 */
public class ManagedQueueMBeanTest extends QpidBrokerTestCase
{
    /**
     * JMX helper.
     */
    private JMXTestUtils _jmxUtils;

    public void setUp() throws Exception
    {
        _jmxUtils = new JMXTestUtils(this);
        _jmxUtils.setUp();
        super.setUp();
        _jmxUtils.open();
    }

    public void tearDown() throws Exception
    {
        if (_jmxUtils != null)
        {
            _jmxUtils.close();
        }
        super.tearDown();
    }

    /**
     * Tests {@link ManagedQueue#viewMessages(long, long)} interface.
     */
    public void testViewSingleMessage() throws Exception
    {
        final String queueName = getTestQueueName();

        // Create queue and send numMessages messages to it.
        final Connection con = getConnection();
        final Session session = con.createSession(true, Session.SESSION_TRANSACTED);
        final Destination dest = session.createQueue(queueName);
        session.createConsumer(dest).close(); // Create a consumer only to cause queue creation

        final List<Message> sentMessages = sendMessage(session, dest, 1);
        final Message sentMessage = sentMessages.get(0);

        // Obtain the management interface.
        final ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        assertNotNull("ManagedQueue expected to be available", managedQueue);
        assertEquals("Unexpected queue depth", 1, managedQueue.getMessageCount().intValue());

        // Check the contents of the message
        final TabularData tab = managedQueue.viewMessages(1l, 1l);
        assertEquals("Unexpected number of rows in table", 1, tab.size());
        final Iterator<CompositeDataSupport> rowItr = (Iterator<CompositeDataSupport>) tab.values().iterator();

        final CompositeDataSupport row1 = rowItr.next();
        assertNotNull("Message should have AMQ message id", row1.get(ManagedQueue.MSG_AMQ_ID));
        assertEquals("Unexpected queue position", 1l, row1.get(ManagedQueue.MSG_QUEUE_POS));
        assertEquals("Unexpected redelivered flag", Boolean.FALSE, row1.get(ManagedQueue.MSG_REDELIVERED));

        // Check the contents of header (encoded in a string array)
        final String[] headerArray = (String[]) row1.get(ManagedQueue.MSG_HEADER);
        assertNotNull("Expected message header array", headerArray);
        final Map<String, String> headers = headerArrayToMap(headerArray);

        final String expectedJMSMessageID = isBroker010() ? sentMessage.getJMSMessageID().replace("ID:", "") : sentMessage.getJMSMessageID();
        final String expectedFormattedJMSTimestamp = FastDateFormat.getInstance(AMQQueueMBean.JMSTIMESTAMP_DATETIME_FORMAT).format(sentMessage.getJMSTimestamp());
        assertEquals("Unexpected JMSMessageID within header", expectedJMSMessageID, headers.get("JMSMessageID"));
        assertEquals("Unexpected JMSPriority within header", String.valueOf(sentMessage.getJMSPriority()), headers.get("JMSPriority"));
        assertEquals("Unexpected JMSTimestamp within header", expectedFormattedJMSTimestamp, headers.get("JMSTimestamp"));
    }

    /**
     *
     * Utility method to convert array of Strings in the form x = y into a
     * map with key/value x =&gt; y.
     *
     */
    private Map<String,String> headerArrayToMap(final String[] headerArray)
    {
        final Map<String, String> headerMap = new HashMap<String, String>();
        final List<String> headerList = Arrays.asList(headerArray);
        for (Iterator<String> iterator = headerList.iterator(); iterator.hasNext();)
        {
            final String nameValuePair = iterator.next();
            final String[] nameValue = nameValuePair.split(" *= *", 2);
            headerMap.put(nameValue[0], nameValue[1]);
        }
        return headerMap;
    }
}
