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
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.security.acl.AbstractACLTestCase;
import org.apache.qpid.systest.rest.QpidRestTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;

public class ExchangeRestACLTest extends QpidRestTestCase
{
    private static final String ALLOWED_USER = "user1";
    private static final String DENIED_USER = "user2";
    private String _queueName;

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();
        getRestTestHelper().configureTemporaryPasswordFile(this, ALLOWED_USER, DENIED_USER);

        AbstractACLTestCase.writeACLFileUtil(this, null,
                "ACL ALLOW-LOG ALL ACCESS MANAGEMENT",
                "ACL ALLOW-LOG " + ALLOWED_USER + " CREATE QUEUE",
                "ACL ALLOW-LOG " + ALLOWED_USER + " CREATE EXCHANGE",
                "ACL DENY-LOG " + DENIED_USER + " CREATE EXCHANGE",
                "ACL ALLOW-LOG " + ALLOWED_USER + " UPDATE EXCHANGE",
                "ACL DENY-LOG " + DENIED_USER + " UPDATE EXCHANGE",
                "ACL ALLOW-LOG " + ALLOWED_USER + " DELETE EXCHANGE",
                "ACL DENY-LOG " + DENIED_USER + " DELETE EXCHANGE",
                "ACL ALLOW-LOG " + ALLOWED_USER + " BIND EXCHANGE",
                "ACL DENY-LOG " + DENIED_USER + " BIND EXCHANGE",
                "ACL ALLOW-LOG " + ALLOWED_USER + " UNBIND EXCHANGE",
                "ACL DENY-LOG " + DENIED_USER + " UNBIND EXCHANGE",
                "ACL DENY-LOG ALL ALL");

        getBrokerConfiguration().setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT,
                HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);
    }

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _queueName = getTestQueueName();
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);
        Map<String, Object> queueData = new HashMap<String, Object>();
        queueData.put(Queue.NAME, _queueName);
        queueData.put(Queue.DURABLE, Boolean.TRUE);
        int status = getRestTestHelper().submitRequest("/rest/queue/test/" + _queueName, "PUT", queueData);
        assertEquals("Unexpected status", 201, status);

    }

    public void testCreateExchangeAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String exchangeName = getTestName();

        int responseCode = createExchange(exchangeName);
        assertEquals("Exchange creation should be allowed", 201, responseCode);

        assertExchangeExists(exchangeName);
    }

    public void testCreateExchangeDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        String exchangeName = getTestName();

        int responseCode = createExchange(exchangeName);
        assertEquals("Exchange creation should be denied", 403, responseCode);

        assertExchangeDoesNotExist(exchangeName);
    }

    public void testDeleteExchangeAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String exchangeName = getTestName();

        int responseCode = createExchange(exchangeName);
        assertEquals("Exchange creation should be allowed", 201, responseCode);

        assertExchangeExists(exchangeName);

        responseCode = getRestTestHelper().submitRequest("/rest/exchange/test/" + exchangeName, "DELETE", null);
        assertEquals("Exchange deletion should be allowed", 200, responseCode);

        assertExchangeDoesNotExist(TEST2_VIRTUALHOST);
    }

    public void testDeleteExchangeDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String exchangeName = getTestName();

        int responseCode = createExchange(exchangeName);
        assertEquals("Exchange creation should be allowed", 201, responseCode);

        assertExchangeExists(exchangeName);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        responseCode = getRestTestHelper().submitRequest("/rest/exchange/test/" + exchangeName, "DELETE", null);
        assertEquals("Exchange deletion should be denied", 403, responseCode);

        assertExchangeExists(exchangeName);
    }

    public void testSetExchangeAttributesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String exchangeName = getTestName();

        int responseCode = createExchange(exchangeName);

        assertExchangeExists(exchangeName);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Exchange.NAME, exchangeName);
        attributes.put(Exchange.ALTERNATE_EXCHANGE, "my-alternate-exchange");

        responseCode = getRestTestHelper().submitRequest("/rest/exchange/test/" + exchangeName, "PUT", attributes);
        assertEquals("Setting of exchange attribites should be allowed but it is currently unsupported", 409, responseCode);
    }

    public void testSetExchangeAttributesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String exchangeName = getTestName();

        int responseCode = createExchange(exchangeName);
        assertExchangeExists(exchangeName);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Exchange.NAME, exchangeName);
        attributes.put(Exchange.ALTERNATE_EXCHANGE, "my-alternate-exchange");

        responseCode = getRestTestHelper().submitRequest("/rest/exchange/test/" + exchangeName, "PUT", attributes);
        assertEquals("Setting of exchange attribites should be allowed", 403, responseCode);
    }

    public void testBindToExchangeAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String bindingName = getTestName();
        int responseCode = createBinding(bindingName);
        assertEquals("Binding creation should be allowed", 201, responseCode);

        assertBindingExists(bindingName);
    }

    public void testBindToExchangeDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        String bindingName = getTestName();
        int responseCode = createBinding(bindingName);
        assertEquals("Binding creation should be denied", 403, responseCode);

        assertBindingDoesNotExist(bindingName);
    }

    private int createExchange(String exchangeName) throws Exception
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Exchange.NAME, exchangeName);
        attributes.put(Exchange.TYPE, "direct");
        return getRestTestHelper().submitRequest("/rest/exchange/test/" + exchangeName, "PUT", attributes);
    }

    private void assertExchangeDoesNotExist(String exchangeName) throws Exception
    {
        assertExchangeExistence(exchangeName, false);
    }

    private void assertExchangeExists(String exchangeName) throws Exception
    {
        assertExchangeExistence(exchangeName, true);
    }

    private void assertExchangeExistence(String exchangeName, boolean exists) throws Exception
    {
        List<Map<String, Object>> exchanges = getRestTestHelper().getJsonAsList("/rest/exchange/test/" + exchangeName);
        assertEquals("Unexpected result", exists, !exchanges.isEmpty());
    }

    private int createBinding(String bindingName) throws IOException, JsonGenerationException, JsonMappingException
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Binding.NAME, bindingName);
        attributes.put(Binding.QUEUE, _queueName);
        attributes.put(Binding.EXCHANGE, "amq.direct");

        int responseCode = getRestTestHelper().submitRequest("/rest/binding/test/amq.direct/" + _queueName + "/" + bindingName, "PUT", attributes);
        return responseCode;
    }

    private void assertBindingDoesNotExist(String bindingName) throws Exception
    {
        assertBindingExistence(bindingName, false);
    }

    private void assertBindingExists(String bindingName) throws Exception
    {
        assertBindingExistence(bindingName, true);
    }

    private void assertBindingExistence(String bindingName, boolean exists) throws Exception
    {
        List<Map<String, Object>> bindings = getRestTestHelper().getJsonAsList("/rest/binding/test/amq.direct/" + _queueName + "/" + bindingName);
        assertEquals("Unexpected result", exists, !bindings.isEmpty());
    }
}
