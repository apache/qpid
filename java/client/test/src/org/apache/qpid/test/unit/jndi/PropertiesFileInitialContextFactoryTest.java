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
package org.apache.qpid.test.unit.jndi;

import junit.framework.JUnit4TestAdapter;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.util.Properties;

public class PropertiesFileInitialContextFactoryTest
{
    InitialContextFactory contextFactory;
    Properties _properties;

    @Before
    public void setupProperties()
    {
        _properties = new Properties();
        _properties.put("java.naming.factory.initial", "org.apache.qpid.jndi.PropertiesFileInitialContextFactory");
        _properties.put("connectionfactory.local", "amqp://guest:guest@clientid/testpath?brokerlist='vm://:1'");
        _properties.put("queue.MyQueue", "example.MyQueue");
        _properties.put("topic.ibmStocks", "stocks.nyse.ibm");
        _properties.put("destination.direct", "direct://amq.direct//directQueue");
    }

    @Test
    public void test()
    {
        Context ctx = null;
        try
        {
            ctx = new InitialContext(_properties);
        }
        catch (NamingException ne)
        {
            Assert.fail("Error loading context:" + ne);
        }

        try
        {
            AMQConnectionFactory cf = (AMQConnectionFactory) ctx.lookup("local");
            Assert.assertEquals("amqp://guest:guest@clientid/testpath?brokerlist='vm://:1'", cf.getConnectionURL().toString());
        }
        catch (NamingException ne)
        {
            Assert.fail("Unable to create Connection Factory:" + ne);
        }

        try
        {
            AMQQueue queue = (AMQQueue) ctx.lookup("MyQueue");
            Assert.assertEquals("example.MyQueue", queue.getRoutingKey());
        }
        catch (NamingException ne)
        {
            Assert.fail("Unable to create queue:" + ne);
        }

        try
        {
            AMQTopic topic = (AMQTopic) ctx.lookup("ibmStocks");
            Assert.assertEquals("stocks.nyse.ibm", topic.getTopicName());
        }
        catch (Exception ne)
        {
            Assert.fail("Unable to create topic:" + ne);
        }

        try
        {
            AMQQueue direct = (AMQQueue) ctx.lookup("direct");
            Assert.assertEquals("directQueue", direct.getRoutingKey());
        }
        catch (NamingException ne)
        {
            Assert.fail("Unable to create direct destination:" + ne);
        }


    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(PropertiesFileInitialContextFactoryTest.class);
    }
}
