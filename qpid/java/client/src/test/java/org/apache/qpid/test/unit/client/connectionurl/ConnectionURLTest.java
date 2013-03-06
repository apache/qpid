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
package org.apache.qpid.test.unit.client.connectionurl;

import junit.framework.TestCase;

import org.apache.qpid.client.AMQBrokerDetails;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.url.URLSyntaxException;

public class ConnectionURLTest extends TestCase
{
    public void testFailoverURL() throws URLSyntaxException
    {
        String url = "amqp://ritchiem:bob@/test?brokerlist='tcp://localhost:5672;tcp://fancyserver:3000/',failover='roundrobin?cyclecount='100''";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getFailoverMethod().equals("roundrobin"));
        assertEquals("100", connectionurl.getFailoverOption(ConnectionURL.OPTIONS_FAILOVER_CYCLE));
        assertTrue(connectionurl.getUsername().equals("ritchiem"));
        assertTrue(connectionurl.getPassword().equals("bob"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));

        assertTrue(connectionurl.getBrokerCount() == 2);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        assertTrue(service.getTransport().equals("tcp"));
        assertTrue(service.getHost().equals("localhost"));
        assertTrue(service.getPort() == 5672);

        service = connectionurl.getBrokerDetails(1);

        assertTrue(service.getTransport().equals("tcp"));
        assertTrue(service.getHost().equals("fancyserver"));
        assertTrue(service.getPort() == 3000);

    }

    public void testSingleTransportUsernamePasswordURL() throws URLSyntaxException
    {
        String url = "amqp://ritchiem:bob@/test?brokerlist='tcp://localhost:5672'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getFailoverMethod() == null);
        assertTrue(connectionurl.getUsername().equals("ritchiem"));
        assertTrue(connectionurl.getPassword().equals("bob"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));

        assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        assertTrue(service.getTransport().equals("tcp"));
        assertTrue(service.getHost().equals("localhost"));
        assertTrue(service.getPort() == 5672);
    }

    public void testSingleTransportUsernameBlankPasswordURL() throws URLSyntaxException
    {
        String url = "amqp://ritchiem:@/test?brokerlist='tcp://localhost:5672'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getFailoverMethod() == null);
        assertTrue(connectionurl.getUsername().equals("ritchiem"));
        assertTrue(connectionurl.getPassword().equals(""));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));

        assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        assertTrue(service.getTransport().equals("tcp"));
        assertTrue(service.getHost().equals("localhost"));
        assertTrue(service.getPort() == 5672);
    }

    public void testFailedURLNullPassword()
    {
        String url = "amqp://ritchiem@/test?brokerlist='tcp://localhost:5672'";

        try
        {
            new AMQConnectionURL(url);
            fail("URL has null password");
        }
        catch (URLSyntaxException e)
        {
            assertTrue(e.getReason().equals("Null password in user information not allowed."));
            assertTrue(e.getIndex() == 7);
        }
    }


    public void testSingleTransportURL() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);


        assertTrue(connectionurl.getFailoverMethod() == null);
        assertTrue(connectionurl.getUsername().equals("guest"));
        assertTrue(connectionurl.getPassword().equals("guest"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));


        assertTrue(connectionurl.getBrokerCount() == 1);


        BrokerDetails service = connectionurl.getBrokerDetails(0);

        assertTrue(service.getTransport().equals("tcp"));
        assertTrue(service.getHost().equals("localhost"));
        assertTrue(service.getPort() == 5672);
    }

    public void testSingleTransportWithClientURLURL() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@clientname/test?brokerlist='tcp://localhost:5672'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);


        assertTrue(connectionurl.getFailoverMethod() == null);
        assertTrue(connectionurl.getUsername().equals("guest"));
        assertTrue(connectionurl.getPassword().equals("guest"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));
        assertTrue(connectionurl.getClientName().equals("clientname"));


        assertTrue(connectionurl.getBrokerCount() == 1);


        BrokerDetails service = connectionurl.getBrokerDetails(0);

        assertTrue(service.getTransport().equals("tcp"));
        assertTrue(service.getHost().equals("localhost"));
        assertTrue(service.getPort() == 5672);
    }

    public void testSingleTransport1OptionURL() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672',routingkey='jim'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getFailoverMethod() == null);
        assertTrue(connectionurl.getUsername().equals("guest"));
        assertTrue(connectionurl.getPassword().equals("guest"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));


        assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        assertTrue(service.getTransport().equals("tcp"));

        assertTrue(service.getHost().equals("localhost"));
        assertTrue(service.getPort() == 5672);
        assertTrue(connectionurl.getOption("routingkey").equals("jim"));
    }

    public void testSingleTransportDefaultedBroker() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='localhost'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getFailoverMethod() == null);
        assertTrue(connectionurl.getUsername().equals("guest"));
        assertTrue(connectionurl.getPassword().equals("guest"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));


        assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        assertTrue(service.getTransport().equals("tcp"));

        assertTrue(service.getHost().equals("localhost"));
        assertTrue(service.getPort() == 5672);
    }

    public void testSingleTransportDefaultedBrokerWithPort() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='localhost:1234'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getFailoverMethod() == null);
        assertTrue(connectionurl.getUsername().equals("guest"));
        assertTrue(connectionurl.getPassword().equals("guest"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));


        assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        assertTrue(service.getTransport().equals("tcp"));

        assertTrue(service.getHost().equals("localhost"));
        assertTrue(service.getPort() == 1234);
    }

    public void testSingleTransportDefaultedBrokerWithIP() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='127.0.0.1'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getFailoverMethod() == null);
        assertTrue(connectionurl.getUsername().equals("guest"));
        assertTrue(connectionurl.getPassword().equals("guest"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));


        assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        assertTrue(service.getTransport().equals("tcp"));

        assertTrue(service.getHost().equals("127.0.0.1"));
        assertTrue(service.getPort() == 5672);
    }

    public void testConnectionURLOptionToStringMasksPassword() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@client/localhost?brokerlist='tcp://localhost:1234'";
        ConnectionURL connectionurl = new AMQConnectionURL(url);

        String expectedToString = "amqp://guest:********@client/localhost?brokerlist='tcp://localhost:1234'";
        String actualToString = connectionurl.toString();
        assertEquals("Unexpected toString form", expectedToString, actualToString);
    }

    public void testConnectionURLOptionToStringMasksSslTrustStorePassword() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@client/vhost?brokerlist='tcp://host:1234?trust_store_password='truststorepassword''";
        ConnectionURL connectionurl = new AMQConnectionURL(url);

        String expectedToString = "amqp://guest:********@client/vhost?brokerlist='tcp://host:1234?trust_store_password='********''";
        String actualToString = connectionurl.toString();
        assertEquals("Unexpected toString form", expectedToString, actualToString);
    }

    public void testConnectionURLOptionToStringMasksSslKeyStorePassword() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@client/vhost?brokerlist='tcp://host:1234?key_store_password='keystorepassword1';tcp://host:1235?key_store_password='keystorepassword2''";
        ConnectionURL connectionurl = new AMQConnectionURL(url);

        String expectedToString = "amqp://guest:********@client/vhost?brokerlist='tcp://host:1234?key_store_password='********';tcp://host:1235?key_store_password='********''";
        String actualToString = connectionurl.toString();
        assertEquals("Unexpected toString form", expectedToString, actualToString);
    }

    /**
     * Test for QPID-3662 to ensure the {@code toString()} representation is correct.
     */
    public void testConnectionURLOptionToStringWithMaxPreftech() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@client/localhost?maxprefetch='1'&brokerlist='tcp://localhost:1234?tcp_nodelay='true''";
        ConnectionURL connectionurl = new AMQConnectionURL(url);

        String expectedToString = "amqp://guest:********@client/localhost?maxprefetch='1'&brokerlist='tcp://localhost:1234?tcp_nodelay='true''";
        String actualToString = connectionurl.toString();
        assertEquals("Unexpected toString form", expectedToString, actualToString);
    }

    public void testSingleTransportMultiOptionURL() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672?foo='jim'&bar='bob'&fred='jimmy'',routingkey='jim',timeout='200',immediatedelivery='true'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getFailoverMethod() == null);
        assertTrue(connectionurl.getUsername().equals("guest"));
        assertTrue(connectionurl.getPassword().equals("guest"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));

        assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        assertTrue(service.getTransport().equals("tcp"));

        assertTrue(service.getHost().equals("localhost"));
        assertTrue(service.getPort() == 5672);

        assertTrue(connectionurl.getOption("routingkey").equals("jim"));
        assertTrue(connectionurl.getOption("timeout").equals("200"));
        assertTrue(connectionurl.getOption("immediatedelivery").equals("true"));
    }

    public void testNoVirtualHostURL()
    {
        String url = "amqp://user@?brokerlist='tcp://localhost:5672'";

        try
        {
            new AMQConnectionURL(url);
            fail("URL has no virtual host should not parse");
        }
        catch (URLSyntaxException e)
        {
            // This should occur.
        }
    }

    public void testNoClientID() throws URLSyntaxException
    {
        String url = "amqp://user:@/test?brokerlist='tcp://localhost:5672'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getUsername().equals("user"));
        assertTrue(connectionurl.getPassword().equals(""));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));

        assertTrue(connectionurl.getBrokerCount() == 1);
    }

    public void testClientIDWithUnderscore() throws URLSyntaxException
    {
        String url = "amqp://user:pass@client_id/test?brokerlist='tcp://localhost:5672'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getUsername().equals("user"));
        assertTrue(connectionurl.getPassword().equals("pass"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));
        assertTrue(connectionurl.getClientName().equals("client_id"));

        assertTrue(connectionurl.getBrokerCount() == 1);
    }

    public void testWrongOptionSeparatorInOptions()
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672;tcp://localhost:5673'+failover='roundrobin'";
        try
        {
            new AMQConnectionURL(url);
            fail("URL Should not parse");
        }
        catch (URLSyntaxException urise)
        {
            assertTrue(urise.getReason().equals("Unterminated option. Possible illegal option separator:'+'"));
        }

    }


    public void testNoUserDetailsProvidedWithClientID()

    {
        String url = "amqp://clientID/test?brokerlist='tcp://localhost:5672;tcp://localhost:5673'";
        try
        {
            new AMQConnectionURL(url);
        }
        catch (URLSyntaxException urise)
        {
            fail("User information is optional in url");
        }

    }

    public void testNoUserDetailsProvidedNOClientID()

    {
        String url = "amqp:///test?brokerlist='tcp://localhost:5672;tcp://localhost:5673'";
        try
        {
            new AMQConnectionURL(url);
        }
        catch (URLSyntaxException urise)
        {
            fail("User information is optional in url");
        }

    }

    public void testCheckVirtualhostFormat() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/t.-_+!=:?brokerlist='tcp://localhost:5672'";

        AMQConnectionURL connection = new AMQConnectionURL(url);
        assertTrue(connection.getVirtualHost().equals("/t.-_+!=:"));
    }

    public void testCheckDefaultPort() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test=:?brokerlist='tcp://localhost'";

        AMQConnectionURL connection = new AMQConnectionURL(url);

        BrokerDetails broker = connection.getBrokerDetails(0);
        assertTrue(broker.getPort() == AMQBrokerDetails.DEFAULT_PORT);

    }

    public void testCheckMissingFinalQuote() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@id/test" + "?brokerlist='tcp://localhost:5672";

        try
        {
            new AMQConnectionURL(url);
        }
        catch (URLSyntaxException e)
        {
            assertEquals(e.getMessage(), "Unterminated option at index 32: brokerlist='tcp://localhost:5672");
        }
    }


    public void testDefaultExchanges() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@id/test" + "?defaultQueueExchange='test.direct'&defaultTopicExchange='test.topic'&temporaryQueueExchange='tmp.direct'&temporaryTopicExchange='tmp.topic'";

        AMQConnectionURL conn = new AMQConnectionURL(url);

        assertEquals(conn.getDefaultQueueExchangeName(),"test.direct");

        assertEquals(conn.getDefaultTopicExchangeName(),"test.topic");

        assertEquals(conn.getTemporaryQueueExchangeName(),"tmp.direct");

        assertEquals(conn.getTemporaryTopicExchangeName(),"tmp.topic");

    }

    public void testSingleTransportMultiOptionOnBrokerURL() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672?foo='jim'&bar='bob'&fred='jimmy'',routingkey='jim',timeout='200',immediatedelivery='true'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getFailoverMethod() == null);
        assertTrue(connectionurl.getUsername().equals("guest"));
        assertTrue(connectionurl.getPassword().equals("guest"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));

        assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        assertTrue(service.getTransport().equals("tcp"));

        assertTrue(service.getHost().equals("localhost"));
        assertTrue(service.getPort() == 5672);
        assertEquals("jim",service.getProperty("foo"));
        assertEquals("bob",service.getProperty("bar"));
        assertEquals("jimmy",service.getProperty("fred"));

        assertTrue(connectionurl.getOption("routingkey").equals("jim"));
        assertTrue(connectionurl.getOption("timeout").equals("200"));
        assertTrue(connectionurl.getOption("immediatedelivery").equals("true"));
    }

    /**
     * Test that options other than failover and brokerlist are returned in the string representation.
     * <p>
     * QPID-2697
     */
    public void testOptionToString() throws Exception
    {
        ConnectionURL url = new AMQConnectionURL("amqp://user:pass@temp/test?maxprefetch='12345'&brokerlist='tcp://localhost:5672'");

        assertTrue("String representation should contain options and values", url.toString().contains("maxprefetch='12345'"));
    }

    public void testHostNamesWithUnderScore() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@clientid/test?brokerlist='tcp://under_score:6672'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getUsername().equals("guest"));
        assertTrue(connectionurl.getPassword().equals("guest"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));

        assertTrue(connectionurl.getBrokerCount() == 1);
        BrokerDetails service = connectionurl.getBrokerDetails(0);
        assertTrue(service.getTransport().equals("tcp"));
        assertTrue(service.getHost().equals("under_score"));
        assertTrue(service.getPort() == 6672);

        url = "amqp://guest:guest@clientid/test?brokerlist='tcp://under_score'";

        connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getUsername().equals("guest"));
        assertTrue(connectionurl.getPassword().equals("guest"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));

        assertTrue(connectionurl.getBrokerCount() == 1);
        service = connectionurl.getBrokerDetails(0);
        assertTrue(service.getTransport().equals("tcp"));
        assertTrue(service.getHost().equals("under_score"));
        assertTrue(service.getPort() == 5672);
    }


    public void testRejectBehaviourPresent() throws Exception
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&rejectbehaviour='server'";

        ConnectionURL connectionURL = new AMQConnectionURL(url);

        assertTrue(connectionURL.getFailoverMethod() == null);
        assertTrue(connectionURL.getUsername().equals("guest"));
        assertTrue(connectionURL.getPassword().equals("guest"));
        assertTrue(connectionURL.getVirtualHost().equals("/test"));

        //check that the reject behaviour option is returned as expected
        assertEquals("Reject behaviour option was not as expected", "server",
                connectionURL.getOption(ConnectionURL.OPTIONS_REJECT_BEHAVIOUR));
    }

    public void testRejectBehaviourNotPresent() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&foo='bar'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        assertTrue(connectionurl.getFailoverMethod() == null);
        assertTrue(connectionurl.getUsername().equals("guest"));
        assertTrue(connectionurl.getPassword().equals("guest"));
        assertTrue(connectionurl.getVirtualHost().equals("/test"));

        //check that the reject behaviour option is null as expected
        assertNull("Reject behaviour option was not as expected",
                connectionurl.getOption(ConnectionURL.OPTIONS_REJECT_BEHAVIOUR));
    }

    /**
     * Verify that when the ssl option is not specified, asking for the option returns null,
     * such that this can later be used to verify it wasnt specified.
     */
    public void testDefaultSsl() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&foo='bar'";
        ConnectionURL connectionURL = new AMQConnectionURL(url);

        assertNull("default ssl value should be null", connectionURL.getOption(ConnectionURL.OPTIONS_SSL));
    }

    /**
     * Verify that when the ssl option is specified, asking for the option returns the value,
     * such that this can later be used to verify what value it was specified as.
     */
    public void testOverridingSsl() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&ssl='true'";
        ConnectionURL connectionURL = new AMQConnectionURL(url);

        assertTrue("value should be true", Boolean.valueOf(connectionURL.getOption(ConnectionURL.OPTIONS_SSL)));

        url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&ssl='false'";
        connectionURL = new AMQConnectionURL(url);

        assertFalse("value should be false", Boolean.valueOf(connectionURL.getOption(ConnectionURL.OPTIONS_SSL)));
    }

    /**
     * Verify that when the {@value ConnectionURL#OPTIONS_VERIFY_QUEUE_ON_SEND} option is not
     * specified, asking for the option returns null, such that this can later be used to
     * verify it wasn't specified.
     */
    public void testDefaultVerifyQueueOnSend() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&foo='bar'";
        ConnectionURL connectionURL = new AMQConnectionURL(url);

        assertNull("default ssl value should be null", connectionURL.getOption(ConnectionURL.OPTIONS_SSL));
    }

    /**
     * Verify that when the {@value ConnectionURL#OPTIONS_VERIFY_QUEUE_ON_SEND} option is
     * specified, asking for the option returns the value, such that this can later be used
     * to verify what value it was specified as.
     */
    public void testOverridingVerifyQueueOnSend() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&verifyQueueOnSend='true'";
        ConnectionURL connectionURL = new AMQConnectionURL(url);

        assertTrue("value should be true", Boolean.valueOf(connectionURL.getOption(ConnectionURL.OPTIONS_VERIFY_QUEUE_ON_SEND)));

        url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&verifyQueueOnSend='false'";
        connectionURL = new AMQConnectionURL(url);

        assertFalse("value should be false", Boolean.valueOf(connectionURL.getOption(ConnectionURL.OPTIONS_VERIFY_QUEUE_ON_SEND)));
    }
}

