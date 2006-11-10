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

import org.junit.Test;
import org.junit.Assert;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.exchange.ExchangeDefaults;
import junit.framework.JUnit4TestAdapter;

public class DestinationURLTest
{
    @Test
    public void fullURL() throws URLSyntaxException
    {

        String url = "exchange.Class://exchangeName/Destination/Queue";

        AMQBindingURL dest = new AMQBindingURL(url);

        Assert.assertTrue(url.equals(dest.toString()));

        Assert.assertTrue(dest.getExchangeClass().equals("exchange.Class"));
        Assert.assertTrue(dest.getExchangeName().equals("exchangeName"));
        Assert.assertTrue(dest.getDestinationName().equals("Destination"));
        Assert.assertTrue(dest.getQueueName().equals("Queue"));
    }

    @Test
    public void queue() throws URLSyntaxException
    {

        String url = "exchangeClass://exchangeName//Queue";

        AMQBindingURL dest = new AMQBindingURL(url);

        Assert.assertTrue(url.equals(dest.toString()));

        Assert.assertTrue(dest.getExchangeClass().equals("exchangeClass"));
        Assert.assertTrue(dest.getExchangeName().equals("exchangeName"));
        Assert.assertTrue(dest.getDestinationName().equals(""));
        Assert.assertTrue(dest.getQueueName().equals("Queue"));
    }

    @Test
    public void queueWithOption() throws URLSyntaxException
    {

        String url = "exchangeClass://exchangeName//Queue?option='value'";

        AMQBindingURL dest = new AMQBindingURL(url);

        Assert.assertTrue(url.equals(dest.toString()));

        Assert.assertTrue(dest.getExchangeClass().equals("exchangeClass"));
        Assert.assertTrue(dest.getExchangeName().equals("exchangeName"));
        Assert.assertTrue(dest.getDestinationName().equals(""));
        Assert.assertTrue(dest.getQueueName().equals("Queue"));
        Assert.assertTrue(dest.getOption("option").equals("value"));
    }


    @Test
    public void destination() throws URLSyntaxException
    {

        String url = "exchangeClass://exchangeName/Destination/";

        AMQBindingURL dest = new AMQBindingURL(url);

        Assert.assertTrue(url.equals(dest.toString()));

        Assert.assertTrue(dest.getExchangeClass().equals("exchangeClass"));
        Assert.assertTrue(dest.getExchangeName().equals("exchangeName"));
        Assert.assertTrue(dest.getDestinationName().equals("Destination"));
        Assert.assertTrue(dest.getQueueName().equals(""));
    }

    @Test
    public void destinationWithOption() throws URLSyntaxException
    {

        String url = "exchangeClass://exchangeName/Destination/?option='value'";

        AMQBindingURL dest = new AMQBindingURL(url);

        Assert.assertTrue(url.equals(dest.toString()));

        Assert.assertTrue(dest.getExchangeClass().equals("exchangeClass"));
        Assert.assertTrue(dest.getExchangeName().equals("exchangeName"));
        Assert.assertTrue(dest.getDestinationName().equals("Destination"));
        Assert.assertTrue(dest.getQueueName().equals(""));

        Assert.assertTrue(dest.getOption("option").equals("value"));
    }

    @Test
    public void destinationWithMultiOption() throws URLSyntaxException
    {

        String url = "exchangeClass://exchangeName/Destination/?option='value',option2='value2'";

        AMQBindingURL dest = new AMQBindingURL(url);

        Assert.assertTrue(dest.getExchangeClass().equals("exchangeClass"));
        Assert.assertTrue(dest.getExchangeName().equals("exchangeName"));
        Assert.assertTrue(dest.getDestinationName().equals("Destination"));
        Assert.assertTrue(dest.getQueueName().equals(""));

        Assert.assertTrue(dest.getOption("option").equals("value"));
        Assert.assertTrue(dest.getOption("option2").equals("value2"));
    }

    @Test
    public void destinationWithNoExchangeDefaultsToDirect() throws URLSyntaxException
    {

        String url = "IBMPerfQueue1?durable='true'";

        AMQBindingURL dest = new AMQBindingURL(url);

        Assert.assertTrue(dest.getExchangeClass().equals(ExchangeDefaults.DIRECT_EXCHANGE_CLASS));
        Assert.assertTrue(dest.getExchangeName().equals(ExchangeDefaults.DIRECT_EXCHANGE_NAME));
        Assert.assertTrue(dest.getDestinationName().equals(""));
        Assert.assertTrue(dest.getQueueName().equals("IBMPerfQueue1"));

        Assert.assertTrue(dest.getOption("durable").equals("true"));
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(DestinationURLTest.class);
    }
}
