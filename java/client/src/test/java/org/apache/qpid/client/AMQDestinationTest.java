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
package org.apache.qpid.client;

import junit.framework.TestCase;

public class AMQDestinationTest extends TestCase
{
    public void testEqualsAndHashCodeForAddressBasedDestinations() throws Exception
    {
        AMQDestination dest = new AMQQueue("ADDR:Foo; {node :{type:queue}}");
        AMQDestination dest1 = new AMQTopic("ADDR:Foo; {node :{type:topic}}");
        AMQDestination dest10 = new AMQTopic("ADDR:Foo; {node :{type:topic}, link:{name:my-topic}}");
        AMQDestination dest2 = new AMQQueue("ADDR:Foo; {create:always,node :{type:queue}}");
        String bUrl = "BURL:direct://amq.direct/test-route/Foo?routingkey='Foo'";
        AMQDestination dest3 = new AMQQueue(bUrl);

        assertTrue(dest.equals(dest));
        assertFalse(dest.equals(dest1));
        assertTrue(dest.equals(dest2));
        assertFalse(dest.equals(dest3));
        assertTrue(dest1.equals(dest10));

        assertTrue(dest.hashCode() == dest.hashCode());
        assertTrue(dest.hashCode() != dest1.hashCode());
        assertTrue(dest.hashCode() == dest2.hashCode());
        assertTrue(dest.hashCode() != dest3.hashCode());
        assertTrue(dest1.hashCode() == dest10.hashCode());

        AMQDestination dest4 = new AMQQueue("ADDR:Foo/Bar; {node :{type:queue}}");
        AMQDestination dest5 = new AMQQueue("ADDR:Foo/Bar2; {node :{type:queue}}");
        assertFalse(dest4.equals(dest5));
        assertTrue(dest4.hashCode() != dest5.hashCode());

        AMQDestination dest6 = new AMQAnyDestination("ADDR:Foo; {node :{type:queue}}");
        AMQDestination dest7 = new AMQAnyDestination("ADDR:Foo; {create: always, node :{type:queue}, link:{capacity: 10}}");
        AMQDestination dest8 = new AMQAnyDestination("ADDR:Foo; {create: always, link:{capacity: 10}}");
        AMQDestination dest9 = new AMQAnyDestination("ADDR:Foo/bar");
        assertTrue(dest6.equals(dest7));
        assertFalse(dest6.equals(dest8)); //dest8 type unknown, could be a topic
        assertFalse(dest7.equals(dest8)); //dest8 type unknown, could be a topic
        assertFalse(dest6.equals(dest9));
        assertTrue(dest6.hashCode() == dest7.hashCode());
        assertTrue(dest6.hashCode() != dest8.hashCode());
        assertTrue(dest7.hashCode() != dest8.hashCode());
        assertTrue(dest6.hashCode() != dest9.hashCode());
    }
}
