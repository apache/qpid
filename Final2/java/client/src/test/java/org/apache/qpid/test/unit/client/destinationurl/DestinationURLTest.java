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

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.URLSyntaxException;

public class DestinationURLTest extends TestCase
{
    public void testFullURL() throws URLSyntaxException
    {

        String url = "exchange.Class://exchangeName/Destination/Queue";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(url.equals(dest.toString()));

        assertTrue(dest.getExchangeClass().equals("exchange.Class"));
        assertTrue(dest.getExchangeName().equals("exchangeName"));
        assertTrue(dest.getDestinationName().equals("Destination"));
        assertTrue(dest.getQueueName().equals("Queue"));
    }

    public void testQueue() throws URLSyntaxException
    {

        String url = "exchangeClass://exchangeName//Queue";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(url.equals(dest.toString()));

        assertTrue(dest.getExchangeClass().equals("exchangeClass"));
        assertTrue(dest.getExchangeName().equals("exchangeName"));
        assertTrue(dest.getDestinationName().equals(""));
        assertTrue(dest.getQueueName().equals("Queue"));
    }

    public void testQueueWithOption() throws URLSyntaxException
    {

        String url = "exchangeClass://exchangeName//Queue?option='value'";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(url.equals(dest.toString()));

        assertTrue(dest.getExchangeClass().equals("exchangeClass"));
        assertTrue(dest.getExchangeName().equals("exchangeName"));
        assertTrue(dest.getDestinationName().equals(""));
        assertTrue(dest.getQueueName().equals("Queue"));
        assertTrue(dest.getOption("option").equals("value"));
    }


    public void testDestination() throws URLSyntaxException
    {

        String url = "exchangeClass://exchangeName/Destination/";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(url.equals(dest.toString()));

        assertTrue(dest.getExchangeClass().equals("exchangeClass"));
        assertTrue(dest.getExchangeName().equals("exchangeName"));
        assertTrue(dest.getDestinationName().equals("Destination"));
        assertTrue(dest.getQueueName().equals(""));
    }

    public void testDestinationWithOption() throws URLSyntaxException
    {

        String url = "exchangeClass://exchangeName/Destination/?option='value'";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(url.equals(dest.toString()));

        assertTrue(dest.getExchangeClass().equals("exchangeClass"));
        assertTrue(dest.getExchangeName().equals("exchangeName"));
        assertTrue(dest.getDestinationName().equals("Destination"));
        assertTrue(dest.getQueueName().equals(""));

        assertTrue(dest.getOption("option").equals("value"));
    }

    public void testDestinationWithMultiOption() throws URLSyntaxException
    {

        String url = "exchangeClass://exchangeName/Destination/?option='value',option2='value2'";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(dest.getExchangeClass().equals("exchangeClass"));
        assertTrue(dest.getExchangeName().equals("exchangeName"));
        assertTrue(dest.getDestinationName().equals("Destination"));
        assertTrue(dest.getQueueName().equals(""));

        assertTrue(dest.getOption("option").equals("value"));
        assertTrue(dest.getOption("option2").equals("value2"));
    }

    public void testDestinationWithNoExchangeDefaultsToDirect() throws URLSyntaxException
    {

        String url = "IBMPerfQueue1?durable='true'";

        AMQBindingURL dest = new AMQBindingURL(url);

        assertTrue(dest.getExchangeClass().equals(ExchangeDefaults.DIRECT_EXCHANGE_CLASS));
        assertTrue(dest.getExchangeName().equals(""));
        assertTrue(dest.getDestinationName().equals(""));
        assertTrue(dest.getQueueName().equals("IBMPerfQueue1"));

        assertTrue(dest.getOption("durable").equals("true"));
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(DestinationURLTest.class);
    }
}
