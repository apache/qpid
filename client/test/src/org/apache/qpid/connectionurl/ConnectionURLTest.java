/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.connectionurl;

import org.junit.Test;
import org.junit.Assert;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.client.AMQBrokerDetails;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.url.URLSyntaxException;
import junit.framework.JUnit4TestAdapter;

public class ConnectionURLTest
{
    @Test
    public void failoverURL() throws URLSyntaxException
    {
        String url = "amqp://ritchiem:bob@/temp?brokerlist='tcp://localhost:5672;tcp://fancyserver:3000/',failover='roundrobin'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        Assert.assertTrue(connectionurl.getFailoverMethod().equals("roundrobin"));
        Assert.assertTrue(connectionurl.getUsername().equals("ritchiem"));
        Assert.assertTrue(connectionurl.getPassword().equals("bob"));
        Assert.assertTrue(connectionurl.getVirtualHost().equals("/temp"));

        Assert.assertTrue(connectionurl.getBrokerCount() == 2);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        Assert.assertTrue(service.getTransport().equals("tcp"));
        Assert.assertTrue(service.getHost().equals("localhost"));
        Assert.assertTrue(service.getPort() == 5672);

        service = connectionurl.getBrokerDetails(1);

        Assert.assertTrue(service.getTransport().equals("tcp"));
        Assert.assertTrue(service.getHost().equals("fancyserver"));
        Assert.assertTrue(service.getPort() == 3000);

    }

    @Test
    public void singleTransportUsernamePasswordURL() throws URLSyntaxException
    {
        String url = "amqp://ritchiem:bob@/temp?brokerlist='tcp://localhost:5672'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        Assert.assertTrue(connectionurl.getFailoverMethod() == null);
        Assert.assertTrue(connectionurl.getUsername().equals("ritchiem"));
        Assert.assertTrue(connectionurl.getPassword().equals("bob"));
        Assert.assertTrue(connectionurl.getVirtualHost().equals("/temp"));

        Assert.assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        Assert.assertTrue(service.getTransport().equals("tcp"));
        Assert.assertTrue(service.getHost().equals("localhost"));
        Assert.assertTrue(service.getPort() == 5672);
    }

    @Test
    public void singleTransportUsernameBlankPasswordURL() throws URLSyntaxException
    {
        String url = "amqp://ritchiem:@/temp?brokerlist='tcp://localhost:5672'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        Assert.assertTrue(connectionurl.getFailoverMethod() == null);
        Assert.assertTrue(connectionurl.getUsername().equals("ritchiem"));
        Assert.assertTrue(connectionurl.getPassword().equals(""));
        Assert.assertTrue(connectionurl.getVirtualHost().equals("/temp"));

        Assert.assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        Assert.assertTrue(service.getTransport().equals("tcp"));
        Assert.assertTrue(service.getHost().equals("localhost"));
        Assert.assertTrue(service.getPort() == 5672);
    }

    @Test
    public void failedURLNullPassword()
    {
        String url = "amqp://ritchiem@/temp?brokerlist='tcp://localhost:5672'";

        try
        {
            new AMQConnectionURL(url);
            Assert.fail("URL has null password");
        }
        catch (URLSyntaxException e)
        {
            Assert.assertTrue(e.getReason().equals("Null password in user information not allowed."));
            Assert.assertTrue(e.getIndex() == 7);
        }
    }


    @Test
    public void singleTransportURL() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);


        Assert.assertTrue(connectionurl.getFailoverMethod() == null);
        Assert.assertTrue(connectionurl.getUsername().equals("guest"));
        Assert.assertTrue(connectionurl.getPassword().equals("guest"));
        Assert.assertTrue(connectionurl.getVirtualHost().equals("/test"));


        Assert.assertTrue(connectionurl.getBrokerCount() == 1);


        BrokerDetails service = connectionurl.getBrokerDetails(0);

        Assert.assertTrue(service.getTransport().equals("tcp"));
        Assert.assertTrue(service.getHost().equals("localhost"));
        Assert.assertTrue(service.getPort() == 5672);
    }

    @Test
    public void singleTransportWithClientURLURL() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@clientname/temp?brokerlist='tcp://localhost:5672'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);


        Assert.assertTrue(connectionurl.getFailoverMethod() == null);
        Assert.assertTrue(connectionurl.getUsername().equals("guest"));
        Assert.assertTrue(connectionurl.getPassword().equals("guest"));
        Assert.assertTrue(connectionurl.getVirtualHost().equals("/temp"));
        Assert.assertTrue(connectionurl.getClientName().equals("clientname"));


        Assert.assertTrue(connectionurl.getBrokerCount() == 1);


        BrokerDetails service = connectionurl.getBrokerDetails(0);

        Assert.assertTrue(service.getTransport().equals("tcp"));
        Assert.assertTrue(service.getHost().equals("localhost"));
        Assert.assertTrue(service.getPort() == 5672);
    }

    @Test
    public void singleTransport1OptionURL() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/temp?brokerlist='tcp://localhost:5672',routingkey='jim'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        Assert.assertTrue(connectionurl.getFailoverMethod() == null);
        Assert.assertTrue(connectionurl.getUsername().equals("guest"));
        Assert.assertTrue(connectionurl.getPassword().equals("guest"));
        Assert.assertTrue(connectionurl.getVirtualHost().equals("/temp"));


        Assert.assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        Assert.assertTrue(service.getTransport().equals("tcp"));

        Assert.assertTrue(service.getHost().equals("localhost"));
        Assert.assertTrue(service.getPort() == 5672);
        Assert.assertTrue(connectionurl.getOption("routingkey").equals("jim"));
    }

    @Test
    public void singleTransportDefaultedBroker() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/temp?brokerlist='localhost'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        Assert.assertTrue(connectionurl.getFailoverMethod() == null);
        Assert.assertTrue(connectionurl.getUsername().equals("guest"));
        Assert.assertTrue(connectionurl.getPassword().equals("guest"));
        Assert.assertTrue(connectionurl.getVirtualHost().equals("/temp"));


        Assert.assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        Assert.assertTrue(service.getTransport().equals("tcp"));

        Assert.assertTrue(service.getHost().equals("localhost"));
        Assert.assertTrue(service.getPort() == 5672);
    }


    @Test
    public void singleTransportMultiOptionURL() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/temp?brokerlist='tcp://localhost:5672',routingkey='jim',timeout='200',immediatedelivery='true'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        Assert.assertTrue(connectionurl.getFailoverMethod() == null);
        Assert.assertTrue(connectionurl.getUsername().equals("guest"));
        Assert.assertTrue(connectionurl.getPassword().equals("guest"));
        Assert.assertTrue(connectionurl.getVirtualHost().equals("/temp"));

        Assert.assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        Assert.assertTrue(service.getTransport().equals("tcp"));

        Assert.assertTrue(service.getHost().equals("localhost"));
        Assert.assertTrue(service.getPort() == 5672);

        Assert.assertTrue(connectionurl.getOption("routingkey").equals("jim"));
        Assert.assertTrue(connectionurl.getOption("timeout").equals("200"));
        Assert.assertTrue(connectionurl.getOption("immediatedelivery").equals("true"));
    }

    @Test
    public void singlevmURL() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/messages?brokerlist='vm://:2'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        Assert.assertTrue(connectionurl.getFailoverMethod() == null);
        Assert.assertTrue(connectionurl.getUsername().equals("guest"));
        Assert.assertTrue(connectionurl.getPassword().equals("guest"));
        Assert.assertTrue(connectionurl.getVirtualHost().equals("/messages"));

        Assert.assertTrue(connectionurl.getBrokerCount() == 1);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        Assert.assertTrue(service.getTransport().equals("vm"));
        Assert.assertTrue(service.getHost().equals(""));
        Assert.assertTrue(service.getPort() == 2);

    }

    @Test
    public void failoverVMURL() throws URLSyntaxException
    {
        String url = "amqp://ritchiem:bob@/temp?brokerlist='vm://:2;vm://:3',failover='roundrobin'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        Assert.assertTrue(connectionurl.getFailoverMethod().equals("roundrobin"));
        Assert.assertTrue(connectionurl.getUsername().equals("ritchiem"));
        Assert.assertTrue(connectionurl.getPassword().equals("bob"));
        Assert.assertTrue(connectionurl.getVirtualHost().equals("/temp"));

        Assert.assertTrue(connectionurl.getBrokerCount() == 2);

        BrokerDetails service = connectionurl.getBrokerDetails(0);

        Assert.assertTrue(service.getTransport().equals("vm"));
        Assert.assertTrue(service.getHost().equals(""));
        Assert.assertTrue(service.getPort() == 2);

        service = connectionurl.getBrokerDetails(1);
        Assert.assertTrue(service.getTransport().equals("vm"));
        Assert.assertTrue(service.getHost().equals(""));
        Assert.assertTrue(service.getPort() == 3);
    }


    @Test
    public void noVirtualHostURL()
    {
        String url = "amqp://user@?brokerlist='tcp://localhost:5672'";

        try
        {
            new AMQConnectionURL(url);
            Assert.fail("URL has no virtual host should not parse");
        }
        catch (URLSyntaxException e)
        {
            // This should occur.
        }
    }

    @Test
    public void noClientID() throws URLSyntaxException
    {
        String url = "amqp://user:@/test?brokerlist='tcp://localhost:5672'";

        ConnectionURL connectionurl = new AMQConnectionURL(url);

        Assert.assertTrue(connectionurl.getUsername().equals("user"));
        Assert.assertTrue(connectionurl.getPassword().equals(""));
        Assert.assertTrue(connectionurl.getVirtualHost().equals("/test"));

        Assert.assertTrue(connectionurl.getBrokerCount() == 1);
    }

    @Test
    public void wrongOptionSeperatorInBroker()
    {
        String url = "amqp://user:@/test?brokerlist='tcp://localhost:5672+option='value''";

        try
        {
            new AMQConnectionURL(url);

            Float version = Float.parseFloat(System.getProperty("java.specification.version"));
            if (version > 1.5)
            {
                Assert.fail("URL Should not parse on Java 1.6 or greater");
            }
        }
        catch (URLSyntaxException urise)
        {
            Assert.assertTrue(urise.getReason().equals("Illegal character in port number"));
        }

    }

    @Test
    public void wrongOptionSeperatorInOptions()
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672;tcp://localhost:5673'+failover='roundrobin'";
        try
        {
            new AMQConnectionURL(url);
            Assert.fail("URL Should not parse");
        }
        catch (URLSyntaxException urise)
        {
            Assert.assertTrue(urise.getReason().equals("Unterminated option. Possible illegal option separator:'+'"));
        }

    }

    @Test
    public void transportsDefaultToTCP() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test?brokerlist='localhost:5672;myhost:5673'&failover='roundrobin'";

        AMQConnectionURL connection = new AMQConnectionURL(url);

        BrokerDetails broker = connection.getBrokerDetails(0);
        Assert.assertTrue(broker.getTransport().equals("tcp"));

        broker = connection.getBrokerDetails(1);
        Assert.assertTrue(broker.getTransport().equals("tcp"));
    }

    @Test
    public void noUserDetailsProvidedWithClientID()

    {
        String url = "amqp://clientID/test?brokerlist='tcp://localhost:5672;tcp://localhost:5673'";
        try
        {
            new AMQConnectionURL(url);
            Assert.fail("URL Should not parse");
        }
        catch (URLSyntaxException urise)
        {
            Assert.assertTrue(urise.getMessage().startsWith("User information not found on url"));
        }

    }

    @Test
    public void noUserDetailsProvidedNOClientID()

    {
        String url = "amqp:///test?brokerlist='tcp://localhost:5672;tcp://localhost:5673'";
        try
        {
            new AMQConnectionURL(url);
            Assert.fail("URL Should not parse");
        }
        catch (URLSyntaxException urise)
        {
            Assert.assertTrue(urise.getMessage().startsWith("User information not found on url"));
        }

    }

    @Test
    public void checkVirtualhostFormat() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/t.-_+!=:?brokerlist='tcp://localhost:5672'";

        AMQConnectionURL connection = new AMQConnectionURL(url);
        Assert.assertTrue(connection.getVirtualHost().equals("/t.-_+!=:"));
    }

    @Test
    public void checkDefaultPort() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@/test=:?brokerlist='tcp://localhost'";

        AMQConnectionURL connection = new AMQConnectionURL(url);

        BrokerDetails broker = connection.getBrokerDetails(0);
        Assert.assertTrue(broker.getPort() == AMQBrokerDetails.DEFAULT_PORT);

    }

    @Test
    public void checkMissingFinalQuote() throws URLSyntaxException
    {
        String url = "amqp://guest:guest@id/test" + "?brokerlist='tcp://localhost:5672";

        try{
        new AMQConnectionURL(url);
        }catch(URLSyntaxException e)
        {
            Assert.assertEquals(e.getMessage(),"Unterminated option at index 32: brokerlist='tcp://localhost:5672");
        }



    }


    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(ConnectionURLTest.class);
    }
}
