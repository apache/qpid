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
package org.apache.qpid.server.model.adapter;

import static org.apache.qpid.server.model.Port.NEED_CLIENT_AUTH;
import static org.apache.qpid.server.model.Port.PORT;
import static org.apache.qpid.server.model.Port.PROTOCOLS;
import static org.apache.qpid.server.model.Port.RECEIVE_BUFFER_SIZE;
import static org.apache.qpid.server.model.Port.SEND_BUFFER_SIZE;
import static org.apache.qpid.server.model.Port.TCP_NO_DELAY;
import static org.apache.qpid.server.model.Port.TRANSPORTS;
import static org.apache.qpid.server.model.Port.WANT_CLIENT_AUTH;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.adapter.PortAttributeDestringifier;

public class PortAttributeDestringifierTest extends TestCase
{
    public void testDestringify()
    {
        // values are non defaults to test the destringifier
        AttributeTestData[] attributes = new AttributeTestData[] {
                new AttributeTestData(PORT, "1234", 1234),
                new AttributeTestData(NEED_CLIENT_AUTH, "true", true),
                new AttributeTestData(WANT_CLIENT_AUTH, "true", true),
                new AttributeTestData(SEND_BUFFER_SIZE, "2345", 2345),
                new AttributeTestData(RECEIVE_BUFFER_SIZE, "3456", 3456),
                new AttributeTestData(TCP_NO_DELAY, "false", false),
                new AttributeTestData(TRANSPORTS, buildStringSetFromArray(Transport.TCP.name(), Transport.SSL.name()),
                                                  buildEnumSetFromArray(Transport.TCP, Transport.SSL)),
                new AttributeTestData(PROTOCOLS, buildStringSetFromArray(Protocol.AMQP_0_10.name(), Protocol.HTTP.name()),
                                                 buildEnumSetFromArray(Protocol.AMQP_0_10, Protocol.HTTP)), };

        Map<String, Object> attributesMap = new HashMap<String, Object>();
        for (AttributeTestData attributeTestData : attributes)
        {
            attributesMap.put(attributeTestData.name, attributeTestData.value);
        }

        PortAttributeDestringifier portAttributeDestringifier = new PortAttributeDestringifier();
        Map<String, Object> destringifiedAttributesMap = portAttributeDestringifier.destringify(attributesMap);

        for (AttributeTestData attributeTestData : attributes)
        {
            Object value = destringifiedAttributesMap.get(attributeTestData.name);
            assertEquals("Unexpected attribute " + attributeTestData.name + " value ", attributeTestData.destringifiedValue,
                    value);
        }
    }

    public void testDestringifyWithoutMandatoryAttributesThrowsException()
    {
        AttributeTestData[] attributesWithoutPort = new AttributeTestData[] {
                new AttributeTestData(NEED_CLIENT_AUTH, "true", true),
                new AttributeTestData(WANT_CLIENT_AUTH, "true", true),
                new AttributeTestData(SEND_BUFFER_SIZE, "2345", 2345),
                new AttributeTestData(RECEIVE_BUFFER_SIZE, "3456", 3456),
                new AttributeTestData(TCP_NO_DELAY, "false", false),
                new AttributeTestData(TRANSPORTS, buildStringSetFromArray(Transport.TCP.name(), Transport.SSL.name()),
                                                  buildEnumSetFromArray(Transport.TCP, Transport.SSL)),
                new AttributeTestData(PROTOCOLS, buildStringSetFromArray(Protocol.AMQP_0_10.name(), Protocol.HTTP.name()),
                                                 buildEnumSetFromArray(Protocol.AMQP_0_10, Protocol.HTTP)), };

        Map<String, Object> attributesMap = new HashMap<String, Object>();
        for (AttributeTestData attributeTestData : attributesWithoutPort)
        {
            attributesMap.put(attributeTestData.name, attributeTestData.value);
        }

        PortAttributeDestringifier portAttributeDestringifier = new PortAttributeDestringifier();
        try
        {
            portAttributeDestringifier.destringify(attributesMap);
            fail("For non existing port attribute an exception should be thrown");
        }
        catch (IllegalArgumentException e)
        {
            // pass
        }
    }

    public void testDestringifyWithUnsupportedProtocolThrowsException()
    {
        AttributeTestData[] attributesWithoutPort = new AttributeTestData[] {
                new AttributeTestData(NEED_CLIENT_AUTH, "true", true),
                new AttributeTestData(WANT_CLIENT_AUTH, "true", true),
                new AttributeTestData(SEND_BUFFER_SIZE, "2345", 2345),
                new AttributeTestData(RECEIVE_BUFFER_SIZE, "3456", 3456),
                new AttributeTestData(TCP_NO_DELAY, "false", false),
                new AttributeTestData(TRANSPORTS, buildStringSetFromArray(Transport.TCP.name(), Transport.SSL.name()),
                                                  buildEnumSetFromArray(Transport.TCP, Transport.SSL)),
                new AttributeTestData(PROTOCOLS, buildStringSetFromArray("UNSUPPORTED_PROTOCOL", Protocol.HTTP.name()),
                                                 buildEnumSetFromArray(null, Protocol.HTTP)), };

        Map<String, Object> attributesMap = new HashMap<String, Object>();
        for (AttributeTestData attributeTestData : attributesWithoutPort)
        {
            attributesMap.put(attributeTestData.name, attributeTestData.value);
        }

        PortAttributeDestringifier portAttributeDestringifier = new PortAttributeDestringifier();
        try
        {
            portAttributeDestringifier.destringify(attributesMap);
            fail("For non supported protocol an exception should be thrown");
        }
        catch (IllegalArgumentException e)
        {
            // pass
        }
    }

    private Set<String> buildStringSetFromArray(String... values)
    {
        return new HashSet<String>(Arrays.asList(values));
    }

    private Set<?> buildEnumSetFromArray(Enum<?>... values)
    {
        return new HashSet<Enum<?>>(Arrays.asList(values));
    }

    private class AttributeTestData
    {
        String name;
        Object value;
        Object destringifiedValue;

        public AttributeTestData(String name, Object value, Object destringifiedValue)
        {
            super();
            this.name = name;
            this.value = value;
            this.destringifiedValue = destringifiedValue;
        }
    }
}
