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

import javax.jms.JMSException;

import junit.framework.TestCase;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ConnectionURL;

public class AMQConnectionFactoryTest extends TestCase
{

    //URL will be returned with the password field swapped for '********'
    // so ensure that these two strings are kept in sync.
    public static final String URL = "amqp://guest:guest@clientID/test?brokerlist='tcp://localhost:5672'";
    public static final String URL_STAR_PWD = "amqp://guest:********@clientID/test?brokerlist='tcp://localhost:5672'";

    public void testConnectionURLStringMasksPassword() throws Exception
    {
        AMQConnectionFactory factory = new AMQConnectionFactory(URL);

        //URL will be returned with the password field swapped for '********'
        assertEquals("Connection URL not correctly set", URL_STAR_PWD, factory.getConnectionURLString());

        // Further test that the processed ConnectionURL is as expected after
        // the set call
        ConnectionURL connectionurl = factory.getConnectionURL();

        assertNull("Failover is set.", connectionurl.getFailoverMethod());
        assertEquals("guest", connectionurl.getUsername());
        assertEquals("guest", connectionurl.getPassword());
        assertEquals("clientID", connectionurl.getClientName());
        assertEquals("/test", connectionurl.getVirtualHost());

        assertEquals(1, connectionurl.getBrokerCount());

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        assertEquals("tcp", service.getTransport());
        assertEquals("localhost", service.getHost());
        assertEquals(5672, service.getPort());
    }

    public void testInstanceCreatedWithDefaultConstructorThrowsExceptionOnCallingConnectWithoutSettingURL() throws Exception
    {
        AMQConnectionFactory factory = new AMQConnectionFactory();

        try
        {
            factory.createConnection();
            fail("Expected exception not thrown");
        }
        catch(JMSException e)
        {
            assertEquals("Unexpected exception", AMQConnectionFactory.NO_URL_CONFIGURED, e.getMessage());
        }
    }

    public void testSerialization() throws Exception
    {
        AMQConnectionFactory factory = new AMQConnectionFactory();
        assertTrue(factory instanceof Serializable);
        factory.setConnectionURLString("amqp://guest:guest@clientID/test?brokerlist='tcp://localhost:5672'");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(factory);
        oos.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        Object deserializedObject = ois.readObject();
        ois.close();

        AMQConnectionFactory deserialisedFactory = (AMQConnectionFactory) deserializedObject;
        assertEquals(factory, deserialisedFactory);
        assertEquals(factory.hashCode(), deserialisedFactory.hashCode());
    }
}
