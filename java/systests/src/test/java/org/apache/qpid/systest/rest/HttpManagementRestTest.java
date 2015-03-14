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

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.management.plugin.servlet.rest.RestServlet;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class HttpManagementRestTest extends QpidRestTestCase
{

    public void testGetHttpManagement() throws Exception
    {
        Map<String, Object> details = getRestTestHelper().getJsonAsSingletonList(
                "plugin/" + TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT);

        assertEquals("Unexpected session timeout", HttpManagement.DEFAULT_TIMEOUT_IN_SECONDS,
                details.get(HttpManagement.TIME_OUT));
        assertEquals("Unexpected http basic auth enabled", true,
                details.get(HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected https basic auth enabled", true,
                details.get(HttpManagement.HTTPS_BASIC_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected http sasl auth enabled", true,
                details.get(HttpManagement.HTTP_SASL_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected https sasl auth enabled", true,
                details.get(HttpManagement.HTTPS_SASL_AUTHENTICATION_ENABLED));
    }

    public void testUpdateAttributes() throws Exception
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(HttpManagement.NAME, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT);
        attributes.put(HttpManagement.HTTPS_BASIC_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.HTTPS_SASL_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.HTTP_SASL_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.TIME_OUT, 10000);

        getRestTestHelper().submitRequest("plugin/" + TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, "PUT", attributes);

        Map<String, Object> details = getRestTestHelper().getJsonAsSingletonList(
                "plugin/" + TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT);

        assertEquals("Unexpected session timeout", 10000, details.get(HttpManagement.TIME_OUT));
        assertEquals("Unexpected http basic auth enabled", true, details.get(HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected https basic auth enabled", false, details.get(HttpManagement.HTTPS_BASIC_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected http sasl auth enabled", false, details.get(HttpManagement.HTTP_SASL_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected https sasl auth enabled", false, details.get(HttpManagement.HTTPS_SASL_AUTHENTICATION_ENABLED));
    }

    public void testUpdateAttributesWithInvalidValues() throws Exception
    {
        Map<String, Object> invalidAttributes = new HashMap<String, Object>();
        invalidAttributes.put(HttpManagement.HTTPS_BASIC_AUTHENTICATION_ENABLED, 1);
        invalidAttributes.put(HttpManagement.HTTPS_SASL_AUTHENTICATION_ENABLED, 2);
        invalidAttributes.put(HttpManagement.HTTP_SASL_AUTHENTICATION_ENABLED, 3);
        invalidAttributes.put(HttpManagement.TIME_OUT, "undefined");

        for (Map.Entry<String, Object> invalidAttribute : invalidAttributes.entrySet())
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(invalidAttribute.getKey(), invalidAttribute.getValue());
            int response = getRestTestHelper().submitRequest("plugin/" + TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, "PUT", attributes);
            assertEquals("Update should fail for attribute " + invalidAttribute.getKey() + " with value " + invalidAttribute.getValue(),
                    RestServlet.SC_UNPROCESSABLE_ENTITY, response);
        }

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(HttpManagement.TIME_OUT, -1l);
        int response  = getRestTestHelper().submitRequest("plugin/" + TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, "PUT", attributes);
        assertEquals("Update should fail for invalid session timeout", RestServlet.SC_UNPROCESSABLE_ENTITY, response);
    }
}
