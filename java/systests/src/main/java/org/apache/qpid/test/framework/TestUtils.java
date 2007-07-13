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
package org.apache.qpid.test.framework;

import static org.apache.qpid.test.framework.MessagingTestConfigProperties.*;

import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * TestUtils provides static helper methods that are usefull for writing tests against QPid.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Create connections from test properties. <td> {@link MessagingTestConfigProperties}
 * <tr><td> Inject a short pause in a test.
 * </table>
 */
public class TestUtils
{
    /**
     * Establishes a JMS connection using a properties file and qpids built in JNDI implementation. This is a simple
     * convenience method for code that does anticipate handling connection failures. All exceptions that indicate
     * that the connection has failed, are wrapped as rutime exceptions, preumably handled by a top level failure
     * handler.
     *
     * @param messagingProps Connection properties as defined in {@link MessagingTestConfigProperties}.
     *
     * @return A JMS conneciton.
     */
    public static Connection createConnection(ParsedProperties messagingProps)
    {
        try
        {
            // Extract the configured connection properties from the test configuration.
            String conUsername = messagingProps.getProperty(USERNAME_PROPNAME);
            String conPassword = messagingProps.getProperty(PASSWORD_PROPNAME);
            String virtualHost = messagingProps.getProperty(VIRTUAL_HOST_PROPNAME);
            String brokerUrl = messagingProps.getProperty(BROKER_PROPNAME);

            // Set up the broker connection url.
            String connectionString =
                "amqp://" + conUsername + ":" + conPassword + "/" + ((virtualHost != null) ? virtualHost : "")
                + "?brokerlist='" + brokerUrl + "'";

            // messagingProps.setProperty(CONNECTION_PROPNAME, connectionString);

            Context ctx = new InitialContext(messagingProps);

            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(CONNECTION_NAME);
            Connection connection = cf.createConnection();

            return connection;
        }
        catch (NamingException e)
        {
            throw new RuntimeException("Got JNDI NamingException whilst looking up the connection factory.", e);
        }
        catch (JMSException e)
        {
            throw new RuntimeException("Could not establish connection due to JMSException.", e);
        }
    }

    /**
     * Pauses for the specified length of time. In the event of failing to pause for at least that length of time
     * due to interuption of the thread, a RutimeException is raised to indicate the failure. The interupted status
     * of the thread is restores in that case. This method should only be used when it is expected that the pause
     * will be succesfull, for example in test code that relies on inejecting a pause.
     *
     * @param t The minimum time to pause for in milliseconds.
     */
    public static void pause(long t)
    {
        try
        {
            Thread.sleep(t);
        }
        catch (InterruptedException e)
        {
            // Restore the interrupted status
            Thread.currentThread().interrupt();

            throw new RuntimeException("Failed to generate the requested pause length.", e);
        }
    }
}
