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
package org.apache.qpid.server.management.plugin.servlet.rest;

import java.util.List;
import java.util.Map;

public class SaslRestTest extends QpidRestTestCase
{
    public void testGet() throws Exception
    {
        Map<String, Object> saslData = getJsonAsMap("/rest/sasl");
        assertNotNull("mechanisms attribute is not found", saslData.get("mechanisms"));

        @SuppressWarnings("unchecked")
        List<String> mechanisms = (List<String>) saslData.get("mechanisms");
        String[] expectedMechanisms = { "AMQPLAIN", "PLAIN", "CRAM-MD5" };
        for (String mechanism : expectedMechanisms)
        {
            assertTrue("Mechanism " + mechanism + " is not found", mechanisms.contains(mechanism));
        }
    }

}
