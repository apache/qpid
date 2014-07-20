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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Destination;
import javax.jms.Queue;
import javax.jms.Topic;

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

    /**
     * Tests that destinations created with the same options string will share the same address options map.
     */
    public void testCacheAddressOptionsMaps() throws Exception
    {
        // Create destinations 1 and 3 with the same options string, and destinations 2 and 4 with a different one
        String optionsStringA = "{create: always, node: {type: topic}}";
        String optionsStringB = "{}";   // empty options
        AMQDestination dest1 = createDestinationWithOptions("testDest1", optionsStringA);
        AMQDestination dest2 = createDestinationWithOptions("testDest2", optionsStringB);
        AMQDestination dest3 = createDestinationWithOptions("testDest3", optionsStringA);
        AMQDestination dest4 = createDestinationWithOptions("testDest4", optionsStringB);

        // Destinations 1 and 3 should refer to the same address options map
        assertSame("Destinations 1 and 3 were created with the same options and should refer to the same options map.",
                dest1.getAddress().getOptions(), dest3.getAddress().getOptions());
        // Destinations 2 and 4 should refer to the same address options map
        assertSame("Destinations 2 and 4 were created with the same options and should refer to the same options map.",
                dest2.getAddress().getOptions(), dest4.getAddress().getOptions());
        // Destinations 1 and 2 should have a different options map
        assertNotSame("Destinations 1 and 2 should not have the same options map.",
                dest1.getAddress().getOptions(), dest2.getAddress().getOptions());

        // Verify the contents of the shared map are as expected
        Map<String, Object> optionsA = new HashMap<String, Object>();
        optionsA.put("create", "always");
        optionsA.put("node", Collections.singletonMap("type", "topic"));
        assertEquals("Contents of the shared address options map are not as expected.",
                optionsA, dest1.getAddress().getOptions());
        assertEquals("Contents of the empty shared address options map are not as expected.",
                Collections.emptyMap(), dest2.getAddress().getOptions());

        // Verify that address options map is immutable
        try
        {
            dest1.getAddress().getOptions().put("testKey", "testValue");
            fail("Should not be able able to modify an address's options map.");
        }
        catch (UnsupportedOperationException e)
        {
            // expected
        }
    }

    private AMQDestination createDestinationWithOptions(String destName, String optionsString) throws Exception
    {
        String addr = "ADDR:" + destName + "; " + optionsString;
        return new AMQAnyDestination(addr);
    }

    /**
     * Tests that when a queue has no link subscription arguments and no link bindings, its Subscription
     * arguments and its bindings list refer to constant empty collections.
     */
    public void testEmptyLinkBindingsAndSubscriptionArgs() throws Exception
    {
        // no link properties
        assertEmptyLinkBindingsAndSubscriptionArgs(new AMQAnyDestination("ADDR:testQueue"));

        // has link properties but no x-bindings; has link x-subscribes but no arguments
        String xSubscribeAddr = "ADDR:testQueueWithXSubscribes; {link: {x-subscribes: {exclusive: true}}}";
        assertEmptyLinkBindingsAndSubscriptionArgs(new AMQAnyDestination(xSubscribeAddr));
    }

    private void assertEmptyLinkBindingsAndSubscriptionArgs(AMQDestination dest)
    {
        assertEquals("Default link subscription arguments should be the constant Collections empty map.",
                Collections.emptyMap(), dest.getLink().getSubscription().getArgs());
        assertSame("Defaultl link bindings should be the constant Collections empty list.",
                   Collections.emptyList(), dest.getLink().getBindings());
    }

    /**
     * Tests that when a node has no bindings specified, its bindings list refers to a constant empty list,
     * so that we are not consuming extra memory unnecessarily.
     */
    public void testEmptyNodeBindings() throws Exception
    {
        // no node properties
        assertEmptyNodeBindings(new AMQAnyDestination("ADDR:testDest1"));
        // has node properties but no x-bindings
        assertEmptyNodeBindings(new AMQAnyDestination("ADDR:testDest2; {node: {type: queue}}"));
        assertEmptyNodeBindings(new AMQAnyDestination("ADDR:testDest3; {node: {type: topic}}"));
    }

    public void testSerializeAMQQueue_BURL() throws Exception
    {
        Queue queue = new AMQQueue("BURL:direct://amq.direct/test-route/Foo?routingkey='Foo'");
        assertTrue(queue instanceof Serializable);

        Queue deserialisedQueue = (Queue) serialiseDeserialiseDestination(queue);

        assertEquals(queue, deserialisedQueue);
        assertEquals(queue.hashCode(), deserialisedQueue.hashCode());
    }

    public void testSerializeAMQQueue_ADDR() throws Exception
    {
        Queue queue = new AMQQueue("ADDR:testDest2; {node: {type: queue}}");
        assertTrue(queue instanceof Serializable);

        Queue deserialisedQueue = (Queue) serialiseDeserialiseDestination(queue);

        assertEquals(queue, deserialisedQueue);
        assertEquals(queue.hashCode(), deserialisedQueue.hashCode());
    }

    public void testSerializeAMQTopic_BURL() throws Exception
    {
        Topic topic = new AMQTopic("BURL:topic://amq.topic/mytopic/?routingkey='mytopic'");
        assertTrue(topic instanceof Serializable);

        Topic deserialisedTopic = (Topic) serialiseDeserialiseDestination(topic);

        assertEquals(topic, deserialisedTopic);
        assertEquals(topic.hashCode(), deserialisedTopic.hashCode());
    }

    public void testSerializeAMQTopic_ADDR() throws Exception
    {
        Topic topic = new AMQTopic("ADDR:my-topic; {assert: always, node:{ type: topic }}");
        assertTrue(topic instanceof Serializable);

        Topic deserialisedTopic = (Topic) serialiseDeserialiseDestination(topic);

        assertEquals(topic, deserialisedTopic);
        assertEquals(topic.hashCode(), deserialisedTopic.hashCode());
    }

    private void assertEmptyNodeBindings(AMQDestination dest)
    {
        assertSame("Empty node bindings should refer to the constant Collections empty list.",
                Collections.emptyList(), dest.getNode().getBindings());
    }

    private Destination serialiseDeserialiseDestination(final Destination dest) throws Exception
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(dest);
        oos.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        Object deserializedObject = ois.readObject();
        ois.close();
        return (Destination)deserializedObject;
    }


}
