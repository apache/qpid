/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.jndi;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.Properties;

import javax.jms.Destination;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.ConfigurationException;
import javax.naming.Context;
import javax.naming.InitialContext;

import junit.framework.TestCase;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.framing.AMQShortString;

public class PropertiesFileInitialContextFactoryTest extends TestCase
{
    private static final String FILE_URL_PATH = System.getProperty("user.dir") + "/client/src/test/java/org/apache/qpid/jndi/";
    private static final String FILE_NAME = "hello.properties";

    private Context ctx;

    protected void setUp() throws Exception
    {
        Properties properties = new Properties();
        properties.load(this.getClass().getResourceAsStream("JNDITest.properties"));

        ctx = new InitialContext(properties);
    }

    public void testContextFromProviderURL() throws Exception
    {
        Properties properties = new Properties();
        properties.load(this.getClass().getResourceAsStream("hello.properties"));
        File f = new File(System.getProperty("java.io.tmpdir") + FILE_NAME);
        FileOutputStream fos = new FileOutputStream(f);
        properties.store(fos, null);

        System.setProperty("java.naming.factory.initial", "org.apache.qpid.jndi.PropertiesFileInitialContextFactory");
        System.setProperty("java.naming.provider.url", "file://" + f.getCanonicalPath());

        InitialContext context = new InitialContext();
        assertNotNull("Lookup from URI based context should not be null", context.lookup("topicExchange"));

        context.close();

        System.setProperty("java.naming.provider.url", f.getCanonicalPath());
        context = new InitialContext();
        assertNotNull("Lookup from fileName should not be null", context.lookup("qpidConnectionfactory"));

        context.close();
        f.delete();

    }
    public void testQueueNamesWithTrailingSpaces() throws Exception
    {
        Queue queue = (Queue)ctx.lookup("QueueNameWithSpace");
        assertEquals("QueueNameWithSpace",queue.getQueueName());
    }

    public void testTopicNamesWithTrailingSpaces() throws Exception
    {
        Topic topic = (Topic)ctx.lookup("TopicNameWithSpace");
        assertEquals("TopicNameWithSpace",topic.getTopicName());
    }

    public void testMultipleTopicNamesWithTrailingSpaces() throws Exception
    {
        Topic topic = (Topic)ctx.lookup("MultipleTopicNamesWithSpace");
        int i = 0;
        for (AMQShortString bindingKey: ((AMQDestination)topic).getBindingKeys())
        {
            i++;
            assertEquals("Topic" + i + "WithSpace",bindingKey.asString());
        }
    }

    public void testConfigurationErrors() throws Exception
    {
        Properties properties = new Properties();
        properties.put("java.naming.factory.initial", "org.apache.qpid.jndi.PropertiesFileInitialContextFactory");
        properties.put("destination.my-queue","amq.topic/test;create:always}");

        try
        {
            ctx = new InitialContext(properties);
            fail("A configuration exception should be thrown with details about the address syntax error");
        }
        catch(ConfigurationException e)
        {
            assertTrue("Incorrect exception", e.getMessage().contains("Failed to parse entry: amq.topic/test;create:always}"));
        }

    }
}
