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
package org.apache.qpid.systest.rest.acl;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.security.acl.AbstractACLTestCase;
import org.apache.qpid.systest.rest.QpidRestTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class QueueRestACLTest extends QpidRestTestCase
{
    private static final String ALLOWED_USER = "user1";
    private static final String DENIED_USER = "user2";

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();
        getRestTestHelper().configureTemporaryPasswordFile(this, ALLOWED_USER, DENIED_USER);

        AbstractACLTestCase.writeACLFileUtil(this, "ACL ALLOW-LOG ALL ACCESS MANAGEMENT",
                "ACL ALLOW-LOG " + ALLOWED_USER + " CREATE QUEUE",
                "ACL DENY-LOG " + DENIED_USER + " CREATE QUEUE",
                "ACL ALLOW-LOG " + ALLOWED_USER + " UPDATE QUEUE",
                "ACL DENY-LOG " + DENIED_USER + " UPDATE QUEUE",
                "ACL ALLOW-LOG " + ALLOWED_USER + " DELETE QUEUE",
                "ACL DENY-LOG " + DENIED_USER + " DELETE QUEUE",
                "ACL DENY-LOG ALL ALL");

        getBrokerConfiguration().setObjectAttribute(Plugin.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT,
                HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);
    }

    public void testCreateQueueAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String queueName = getTestName();

        int responseCode = createQueue(queueName);
        assertEquals("Queue creation should be allowed", 201, responseCode);

        assertQueueExists(queueName);
    }

    public void testCreateQueueDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        String queueName = getTestName();

        int responseCode = createQueue(queueName);
        assertEquals("Queue creation should be denied", 403, responseCode);

        assertQueueDoesNotExist(queueName);
    }

    public void testDeleteQueueAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String queueName = getTestName();

        int responseCode = createQueue(queueName);
        assertEquals("Queue creation should be allowed", 201, responseCode);

        assertQueueExists(queueName);

        responseCode = getRestTestHelper().submitRequest("/rest/queue/test/" + queueName, "DELETE", null);
        assertEquals("Queue deletion should be allowed", 200, responseCode);

        assertQueueDoesNotExist(TEST2_VIRTUALHOST);
    }

    public void testDeleteQueueDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String queueName = getTestName();

        int responseCode = createQueue(queueName);
        assertEquals("Queue creation should be allowed", 201, responseCode);

        assertQueueExists(queueName);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        responseCode = getRestTestHelper().submitRequest("/rest/queue/test/" + queueName, "DELETE", null);
        assertEquals("Queue deletion should be denied", 403, responseCode);

        assertQueueExists(queueName);
    }

    public void testSetQueueAttributesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String queueName = getTestName();

        int responseCode = createQueue(queueName);

        assertQueueExists(queueName);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.NAME, queueName);
        attributes.put(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES, 100000);
        attributes.put(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES, 80000);

        responseCode = getRestTestHelper().submitRequest("/rest/queue/test/" + queueName, "PUT", attributes);
        assertEquals("Setting of queue attribites should be allowed", 200, responseCode);

        Map<String, Object> queueData = getRestTestHelper().getJsonAsSingletonList("/rest/queue/test/" + queueName);
        assertEquals("Unexpected " + Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES, 100000, queueData.get(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES) );
        assertEquals("Unexpected " + Queue.QUEUE_FLOW_RESUME_SIZE_BYTES, 80000, queueData.get(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES) );
    }

    public void testSetQueueAttributesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String queueName = getTestName();

        int responseCode = createQueue(queueName);
        assertQueueExists(queueName);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.NAME, queueName);
        attributes.put(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES, 100000);
        attributes.put(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES, 80000);

        responseCode = getRestTestHelper().submitRequest("/rest/queue/test/" + queueName, "PUT", attributes);
        assertEquals("Setting of queue attribites should be allowed", 403, responseCode);

        Map<String, Object> queueData = getRestTestHelper().getJsonAsSingletonList("/rest/queue/test/" + queueName);
        assertEquals("Unexpected " + Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES, 0, queueData.get(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES) );
        assertEquals("Unexpected " + Queue.QUEUE_FLOW_RESUME_SIZE_BYTES, 0, queueData.get(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES) );
    }

    private int createQueue(String queueName) throws Exception
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.NAME, queueName);

        return getRestTestHelper().submitRequest("/rest/queue/test/" + queueName, "PUT", attributes);
    }

    private void assertQueueDoesNotExist(String queueName) throws Exception
    {
        assertQueueExistence(queueName, false);
    }

    private void assertQueueExists(String queueName) throws Exception
    {
        assertQueueExistence(queueName, true);
    }

    private void assertQueueExistence(String queueName, boolean exists) throws Exception
    {
        List<Map<String, Object>> queues = getRestTestHelper().getJsonAsList("/rest/queue/test/" + queueName);
        assertEquals("Unexpected result", exists, !queues.isEmpty());
    }
}
