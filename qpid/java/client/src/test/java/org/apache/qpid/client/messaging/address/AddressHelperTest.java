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
package org.apache.qpid.client.messaging.address;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQDestination.AddressOption;
import org.apache.qpid.client.AMQDestination.Binding;
import org.apache.qpid.client.messaging.address.Link.Reliability;
import org.apache.qpid.messaging.Address;
import org.apache.qpid.test.utils.QpidTestCase;

public class AddressHelperTest extends QpidTestCase
{
    public void testAddressOptions() throws Exception
    {
        Address addr = Address.parse("queue/test;{create:sender, assert:always, delete:receiver, mode:browse}");
        AddressHelper helper = new AddressHelper(addr);
        assertEquals(AddressOption.SENDER,AddressOption.getOption(helper.getCreate()));
        assertEquals(AddressOption.ALWAYS,AddressOption.getOption(helper.getAssert()));
        assertEquals(AddressOption.RECEIVER,AddressOption.getOption(helper.getDelete()));
        assertTrue("'mode' option wasn't read properly",helper.isBrowseOnly());
    }

    public void testNodeProperties() throws Exception
    {
        Address addr = Address.parse("my-queue;{" +
                "node: " +
                "{" +
                     "type: queue ," +
                     "durable: true ," +
                     "x-declare: " +
                     "{" +
                         "exclusive: true," +
                         "auto-delete: true," +
                         "alternate-exchange: 'amq.fanout'," +
                         "arguments: {" +
                            "'qpid.max_size': 1000," +
                            "'qpid.max_count': 100" +
                         "}" +
                      "}, " +
                      "x-bindings: [{exchange : 'amq.direct', queue:my-queue, key : test}, " +
                                   "{exchange : 'amq.fanout', queue:my-queue}," +
                                   "{exchange: 'amq.match', queue:my-queue, arguments: {x-match: any, dep: sales, loc: CA}}," +
                                   "{exchange : 'amq.topic',queue:my-queue, key : 'a.#'}" +
                                  "]" +

                "}" +
          "}");
        AddressHelper helper = new AddressHelper(addr);
        Node node = helper.getNode();
        assertEquals("'type' property wasn't read properly",AMQDestination.QUEUE_TYPE,helper.getNodeType());
        assertTrue("'durable' property wasn't read properly",node.isDurable());
        assertTrue("'auto-delete' property wasn't read properly",node.isAutoDelete());
        assertTrue("'exclusive' property wasn't read properly",node.isExclusive());
        assertEquals("'alternate-exchange' property wasn't read properly","amq.fanout",node.getAlternateExchange());
        assertEquals("'arguments' in 'x-declare' property wasn't read properly",2,node.getDeclareArgs().size());
        assertEquals("'bindings' property wasn't read properly",4,node.getBindings().size());
        for (Binding binding: node.getBindings())
        {
            assertTrue("property 'exchange' in bindings wasn't read properly",binding.getExchange().startsWith("amq."));
            assertEquals("property 'queue' in bindings wasn't read properly","my-queue",binding.getQueue());
            if (binding.getExchange().equals("amq.direct"))
            {
                assertEquals("'key' property in bindings wasn't read properly","test",binding.getBindingKey());
            }
            if (binding.getExchange().equals("amq.match"))
            {
                assertEquals("'arguments' property in bindings wasn't read properly",3,binding.getArgs().size());
            }
        }
    }

    public void testLinkProperties() throws Exception
    {
        Address addr = Address.parse("my-queue;{" +
                "link: " +
                "{" +
                     "name: my-queue ," +
                     "durable: true ," +
                     "reliability: at-least-once," +
                     "capacity: {source:10, target:15}," +
                     "x-declare: " +
                     "{" +
                         "exclusive: true," +
                         "auto-delete: true," +
                         "alternate-exchange: 'amq.fanout'," +
                         "arguments: {" +
                            "'qpid.max_size': 1000," +
                            "'qpid.max_count': 100" +
                         "}" +
                      "}, " +
                      "x-bindings: [{exchange : 'amq.direct', queue:my-queue, key : test}, " +
                                   "{exchange : 'amq.fanout', queue:my-queue}," +
                                   "{exchange: 'amq.match', queue:my-queue, arguments: {x-match: any, dep: sales, loc: CA}}," +
                                   "{exchange : 'amq.topic',queue:my-queue, key : 'a.#'}" +
                                  "]," +
                      "x-subscribes:{exclusive: true, arguments: {a:b,x:y}}" +
                "}" +
          "}");

        AddressHelper helper = new AddressHelper(addr);
        Link link = helper.getLink();
        assertEquals("'name' property wasn't read properly","my-queue",link.getName());
        assertTrue("'durable' property wasn't read properly",link.isDurable());
        assertEquals("'reliability' property wasn't read properly",Reliability.AT_LEAST_ONCE,link.getReliability());
        assertTrue("'auto-delete' property in 'x-declare' wasn't read properly",link.getSubscriptionQueue().isAutoDelete());
        assertTrue("'exclusive' property in 'x-declare' wasn't read properly",link.getSubscriptionQueue().isExclusive());
        assertEquals("'alternate-exchange' property in 'x-declare' wasn't read properly","amq.fanout",link.getSubscriptionQueue().getAlternateExchange());
        assertEquals("'arguments' in 'x-declare' property wasn't read properly",2,link.getSubscriptionQueue().getDeclareArgs().size());
        assertEquals("'bindings' property wasn't read properly",4,link.getBindings().size());
        for (Binding binding: link.getBindings())
        {
            assertTrue("property 'exchange' in bindings wasn't read properly",binding.getExchange().startsWith("amq."));
            assertEquals("property 'queue' in bindings wasn't read properly","my-queue",binding.getQueue());
            if (binding.getExchange().equals("amq.direct"))
            {
                assertEquals("'key' property in bindings wasn't read properly","test",binding.getBindingKey());
            }
            if (binding.getExchange().equals("amq.match"))
            {
                assertEquals("'arguments' property in bindings wasn't read properly",3,binding.getArgs().size());
            }
        }
        assertTrue("'exclusive' property in 'x-subscribe' wasn't read properly",link.getSubscription().isExclusive());
        assertEquals("'arguments' in 'x-subscribe' property wasn't read properly",2,link.getSubscription().getArgs().size());
    }

}
