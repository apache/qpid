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
package org.apache.qpid.test.unit.client.destinationurl;

import junit.framework.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.RejectBehaviour;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;

import java.net.URISyntaxException;

public class DestinationURLTest extends TestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(DestinationURLTest.class);

    public void testFullURL() throws URISyntaxException
    {

        String url = "exchange.Class://exchangeName/Destination/Queue";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(url.equals(dest.toString()));

        assertTrue(dest.getExchangeClass().equalsCharSequence("exchange.Class"));
        assertTrue(dest.getExchangeName().equalsCharSequence("exchangeName"));
        assertTrue(dest.getDestinationName().equalsCharSequence("Destination"));
        assertTrue(dest.getQueueName().equalsCharSequence("Queue"));
    }

    public void testQueue() throws URISyntaxException
    {

        String url = "exchangeClass://exchangeName//Queue";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(url.equals(dest.toString()));

        assertTrue(dest.getExchangeClass().equalsCharSequence("exchangeClass"));
        assertTrue(dest.getExchangeName().equalsCharSequence("exchangeName"));
        assertTrue(dest.getDestinationName().equalsCharSequence(""));
        assertTrue(dest.getQueueName().equalsCharSequence("Queue"));
    }

    public void testQueueWithOption() throws URISyntaxException
    {

        String url = "exchangeClass://exchangeName//Queue?option='value'";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(url.equals(dest.toString()));

        assertTrue(dest.getExchangeClass().equalsCharSequence("exchangeClass"));
        assertTrue(dest.getExchangeName().equalsCharSequence("exchangeName"));
        assertTrue(dest.getDestinationName().equalsCharSequence(""));
        assertTrue(dest.getQueueName().equalsCharSequence("Queue"));
        assertTrue(dest.getOption("option").equals("value"));
    }


    public void testDestination() throws URISyntaxException
    {

        String url = "exchangeClass://exchangeName/Destination/";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(url.equals(dest.toString()));

        assertTrue(dest.getExchangeClass().equalsCharSequence("exchangeClass"));
        assertTrue(dest.getExchangeName().equalsCharSequence("exchangeName"));
        assertTrue(dest.getDestinationName().equalsCharSequence("Destination"));
        assertTrue(dest.getQueueName().equalsCharSequence(""));
    }

    public void testDestinationWithOption() throws URISyntaxException
    {

        String url = "exchangeClass://exchangeName/Destination/?option='value'";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(url.equals(dest.toString()));

        assertTrue(dest.getExchangeClass().equalsCharSequence("exchangeClass"));
        assertTrue(dest.getExchangeName().equalsCharSequence("exchangeName"));
        assertTrue(dest.getDestinationName().equalsCharSequence("Destination"));
        assertTrue(dest.getQueueName().equalsCharSequence(""));

        assertTrue(dest.getOption("option").equals("value"));
    }

    public void testDestinationWithMultiOption() throws URISyntaxException
    {

        String url = "exchangeClass://exchangeName/Destination/?option='value',option2='value2'";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(dest.getExchangeClass().equalsCharSequence("exchangeClass"));
        assertTrue(dest.getExchangeName().equalsCharSequence("exchangeName"));
        assertTrue(dest.getDestinationName().equalsCharSequence("Destination"));
        assertTrue(dest.getQueueName().equalsCharSequence(""));

        assertTrue(dest.getOption("option").equals("value"));
        assertTrue(dest.getOption("option2").equals("value2"));
    }

    public void testDestinationWithNoExchangeDefaultsToDirect() throws URISyntaxException
    {

        String url = "IBMPerfQueue1?durable='true'";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(dest.getExchangeClass().equals(AMQShortString.valueOf(ExchangeDefaults.DIRECT_EXCHANGE_CLASS)));
        assertTrue(dest.getExchangeName().equalsCharSequence(""));
        assertTrue(dest.getDestinationName().equalsCharSequence(""));
        assertTrue(dest.getQueueName().equalsCharSequence("IBMPerfQueue1"));

        assertTrue(dest.getOption("durable").equals("true"));
    }

    public void testDestinationWithMultiBindingKeys() throws URISyntaxException
    {

        String url = "exchangeClass://exchangeName/Destination/?bindingkey='key1',bindingkey='key2'";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(dest.getExchangeClass().equalsCharSequence("exchangeClass"));
        assertTrue(dest.getExchangeName().equalsCharSequence("exchangeName"));
        assertTrue(dest.getDestinationName().equalsCharSequence("Destination"));
        assertTrue(dest.getQueueName().equalsCharSequence(""));

        assertTrue(dest.getBindingKeys().length == 2);
    }

    // You can only specify only a routing key or binding key, but not both.
    public void testDestinationIfOnlyRoutingKeyOrBindingKeyIsSpecified() throws URISyntaxException
    {

        String url = "exchangeClass://exchangeName/Destination/?bindingkey='key1',routingkey='key2'";
        boolean exceptionThrown = false;
        try
        {

            new AMQBindingURL(url);
        }
        catch(URISyntaxException e)
        {
            exceptionThrown = true;
            _logger.info("Exception thrown",e);
        }

        assertTrue("Failed to throw an URISyntaxException when both the bindingkey and routingkey is specified",exceptionThrown);
    }

    public void testExchangeOptionsNotPresent() throws URISyntaxException
    {
        String url = "exchangeClass://exchangeName/Destination/Queue";

        AMQBindingURL burl = new AMQBindingURL(url);

        assertTrue(url.equals(burl.toString()));

        assertNull(burl.getOption(BindingURL.OPTION_EXCHANGE_DURABLE));
        assertNull(burl.getOption(BindingURL.OPTION_EXCHANGE_AUTODELETE));
        assertNull(burl.getOption(BindingURL.OPTION_EXCHANGE_INTERNAL));

        class MyTestAMQDestination extends AMQDestination
        {
            public MyTestAMQDestination(BindingURL url)
            {
                super(url);
            }
            public boolean isNameRequired()
            {
                return false;
            }
        };

        AMQDestination dest = new MyTestAMQDestination(burl);
        assertFalse(dest.isExchangeAutoDelete());
        assertFalse(dest.isExchangeDurable());
        assertFalse(dest.isExchangeInternal());
    }

    public void testExchangeAutoDeleteOptionPresent() throws URISyntaxException
    {
        String url = "exchangeClass://exchangeName/Destination/Queue?" + BindingURL.OPTION_EXCHANGE_AUTODELETE + "='true'";

        AMQBindingURL burl = new AMQBindingURL(url);

        assertTrue(url.equals(burl.toString()));

        assertEquals("true", burl.getOption(BindingURL.OPTION_EXCHANGE_AUTODELETE));
        assertNull(burl.getOption(BindingURL.OPTION_EXCHANGE_DURABLE));
        assertNull(burl.getOption(BindingURL.OPTION_EXCHANGE_INTERNAL));

        class MyTestAMQDestination extends AMQDestination
        {
            public MyTestAMQDestination(BindingURL url)
            {
                super(url);
            }
            public boolean isNameRequired()
            {
                return false;
            }
        };

        AMQDestination dest = new MyTestAMQDestination(burl);
        assertTrue(dest.isExchangeAutoDelete());
        assertFalse(dest.isExchangeDurable());
        assertFalse(dest.isExchangeInternal());
    }

    public void testExchangeDurableOptionPresent() throws URISyntaxException
    {
        String url = "exchangeClass://exchangeName/Destination/Queue?" + BindingURL.OPTION_EXCHANGE_DURABLE + "='true'";

        AMQBindingURL burl = new AMQBindingURL(url);

        assertTrue(url.equals(burl.toString()));

        assertEquals("true", burl.getOption(BindingURL.OPTION_EXCHANGE_DURABLE));
        assertNull(burl.getOption(BindingURL.OPTION_EXCHANGE_AUTODELETE));
        assertNull(burl.getOption(BindingURL.OPTION_EXCHANGE_INTERNAL));

        class MyTestAMQDestination extends AMQDestination
        {
            public MyTestAMQDestination(BindingURL url)
            {
                super(url);
            }
            public boolean isNameRequired()
            {
                return false;
            }
        };

        AMQDestination dest = new MyTestAMQDestination(burl);
        assertTrue(dest.isExchangeDurable());
        assertFalse(dest.isExchangeAutoDelete());
        assertFalse(dest.isExchangeInternal());
    }

    public void testExchangeInternalOptionPresent() throws URISyntaxException
    {
        String url = "exchangeClass://exchangeName/Destination/Queue?" + BindingURL.OPTION_EXCHANGE_INTERNAL + "='true'";

        AMQBindingURL burl = new AMQBindingURL(url);

        assertTrue(url.equals(burl.toString()));

        assertEquals("true", burl.getOption(BindingURL.OPTION_EXCHANGE_INTERNAL));
        assertNull(burl.getOption(BindingURL.OPTION_EXCHANGE_AUTODELETE));
        assertNull(burl.getOption(BindingURL.OPTION_EXCHANGE_DURABLE));

        class MyTestAMQDestination extends AMQDestination
        {
            public MyTestAMQDestination(BindingURL url)
            {
                super(url);
            }
            public boolean isNameRequired()
            {
                return false;
            }
        };

        AMQDestination dest = new MyTestAMQDestination(burl);
        assertTrue(dest.isExchangeInternal());
        assertFalse(dest.isExchangeDurable());
        assertFalse(dest.isExchangeAutoDelete());
    }

    public void testRejectBehaviourPresent() throws URISyntaxException
    {
        String url = "exchangeClass://exchangeName/Destination/Queue?rejectbehaviour='server'";

        AMQBindingURL burl = new AMQBindingURL(url);

        assertTrue(url.equals(burl.toString()));
        assertTrue(burl.getExchangeClass().equalsCharSequence("exchangeClass"));
        assertTrue(burl.getExchangeName().equalsCharSequence("exchangeName"));
        assertTrue(burl.getDestinationName().equalsCharSequence("Destination"));
        assertTrue(burl.getQueueName().equalsCharSequence("Queue"));

        //check that the MaxDeliveryCount property has the right value
        assertEquals("server",burl.getOption(BindingURL.OPTION_REJECT_BEHAVIOUR));

        //check that the MaxDeliveryCount value is correctly returned from an AMQDestination
        class MyTestAMQDestination extends AMQDestination
        {
            public MyTestAMQDestination(BindingURL url)
            {
                super(url);
            }
            public boolean isNameRequired()
            {
                return false;
            }
        };

        AMQDestination dest = new MyTestAMQDestination(burl);
        assertEquals("Reject behaviour is unexpected", RejectBehaviour.SERVER, dest.getRejectBehaviour());
    }

    public void testRejectBehaviourNotPresent() throws URISyntaxException
    {
        String url = "exchangeClass://exchangeName/Destination/Queue";

        AMQBindingURL burl = new AMQBindingURL(url);

        assertTrue(url.equals(burl.toString()));

        assertTrue(burl.getExchangeClass().equalsCharSequence("exchangeClass"));
        assertTrue(burl.getExchangeName().equalsCharSequence("exchangeName"));
        assertTrue(burl.getDestinationName().equalsCharSequence("Destination"));
        assertTrue(burl.getQueueName().equalsCharSequence("Queue"));

        class MyTestAMQDestination extends AMQDestination
        {
            public MyTestAMQDestination(BindingURL url)
            {
                super(url);
            }
            public boolean isNameRequired()
            {
                return false;
            }
        };

        AMQDestination dest = new MyTestAMQDestination(burl);
        assertNull("Reject behaviour is unexpected", dest.getRejectBehaviour());
    }

    public void testBindingUrlWithoutDestinationAndQueueName() throws Exception
    {
        AMQBindingURL bindingURL = new AMQBindingURL("topic://amq.topic//?routingkey='testTopic'");
        assertEquals("Unexpected queue name", AMQShortString.EMPTY_STRING, bindingURL.getQueueName());
        assertEquals("Unexpected destination", AMQShortString.EMPTY_STRING, bindingURL.getDestinationName());
        assertEquals("Unexpected routing key", AMQShortString.valueOf("testTopic"), bindingURL.getRoutingKey());
    }

    public void testBindingUrlWithoutDestinationAndMissedQueueName() throws Exception
    {
        AMQBindingURL bindingURL = new AMQBindingURL("topic://amq.topic/?routingkey='testTopic'");
        assertEquals("Unexpected queue name", AMQShortString.EMPTY_STRING, bindingURL.getQueueName());
        assertEquals("Unexpected destination", AMQShortString.EMPTY_STRING, bindingURL.getDestinationName());
        assertEquals("Unexpected routing key", AMQShortString.valueOf("testTopic"), bindingURL.getRoutingKey());
    }

    public void testBindingUrlWithoutQueueName() throws Exception
    {
        AMQBindingURL bindingURL = new AMQBindingURL("topic://amq.topic/destination/?routingkey='testTopic'");
        assertEquals("Unexpected queue name", AMQShortString.EMPTY_STRING, bindingURL.getQueueName());
        assertEquals("Unexpected destination", AMQShortString.valueOf("destination"), bindingURL.getDestinationName());
        assertEquals("Unexpected routing key", AMQShortString.valueOf("testTopic"), bindingURL.getRoutingKey());
    }

    public void testBindingUrlWithQueueNameWithoutDestination() throws Exception
    {
        AMQBindingURL bindingURL = new AMQBindingURL("topic://amq.topic//queueName?routingkey='testTopic'");
        assertEquals("Unexpected queue name", AMQShortString.valueOf("queueName"), bindingURL.getQueueName());
        assertEquals("Unexpected destination", AMQShortString.EMPTY_STRING, bindingURL.getDestinationName());
        assertEquals("Unexpected routing key", AMQShortString.valueOf("testTopic"), bindingURL.getRoutingKey());
    }

    public void testBindingUrlWithQueueNameAndDestination() throws Exception
    {
        AMQBindingURL bindingURL = new AMQBindingURL("topic://amq.topic/destination/queueName?routingkey='testTopic'");
        assertEquals("Unexpected queue name", AMQShortString.valueOf("queueName"), bindingURL.getQueueName());
        assertEquals("Unexpected destination", AMQShortString.valueOf("destination"), bindingURL.getDestinationName());
        assertEquals("Unexpected routing key", AMQShortString.valueOf("testTopic"), bindingURL.getRoutingKey());
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(DestinationURLTest.class);
    }
}
