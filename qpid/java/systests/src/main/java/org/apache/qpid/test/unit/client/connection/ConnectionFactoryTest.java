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
package org.apache.qpid.test.unit.client.connection;

import javax.jms.Connection;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class ConnectionFactoryTest extends QpidBrokerTestCase
{

    /**
     * The username & password specified should not override the default
     * specified in the URL.
     */
    public void testCreateConnectionWithUsernamePassword() throws Exception
    {
        
        String brokerUrl = getBroker().toString();
        String URL = "amqp://guest:guest@clientID/test?brokerlist='" + brokerUrl + "'";
        AMQConnectionFactory factory = new AMQConnectionFactory(URL);
        
        AMQConnection con = (AMQConnection)factory.createConnection();
        assertEquals("Usernames used is different from the one in URL","guest",con.getConnectionURL().getUsername());
        assertEquals("Password used is different from the one in URL","guest",con.getConnectionURL().getPassword());
     
        try
        {
            AMQConnection con2 = (AMQConnection)factory.createConnection("user","pass");
            assertEquals("Usernames used is different from the one in URL","user",con2.getConnectionURL().getUsername());
            assertEquals("Password used is different from the one in URL","pass",con2.getConnectionURL().getPassword());
        }
        catch(Exception e)
        {
            // ignore
        }
        
        AMQConnection con3 = (AMQConnection)factory.createConnection();
        assertEquals("Usernames used is different from the one in URL","guest",con3.getConnectionURL().getUsername());
        assertEquals("Password used is different from the one in URL","guest",con3.getConnectionURL().getPassword());
    }

    /**
     * Verifies that a connection can be made using an instance of AMQConnectionFactory created with the
     * default constructor and provided with the connection url via setter.
     */
    public void testCreatingConnectionWithInstanceMadeUsingDefaultConstructor() throws Exception
    {
        String broker = getBroker().toString();
        String url = "amqp://guest:guest@clientID/test?brokerlist='" + broker + "'";

        AMQConnectionFactory factory = new AMQConnectionFactory();
        factory.setConnectionURLString(url);

        Connection con = factory.createConnection();
        con.close();
    }
}
