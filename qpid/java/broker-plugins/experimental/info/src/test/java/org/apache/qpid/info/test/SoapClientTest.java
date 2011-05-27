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

package org.apache.qpid.info.test;

import junit.framework.TestCase;
import org.apache.qpid.info.util.SoapClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

public class SoapClientTest extends TestCase
{

    private int _port;

    private final String _hostName = "localhost";

    private final String _urlPath = "/testSoap";

    private ServerSocket _server = null;

    /*
     * Generate a soap client from a custom URL, hostname, port and soap context
     * path to be derived
     */
    private SoapClient getSoapClient()
    {
        Properties destprops = new Properties();
        destprops.setProperty("soap.hostname", _hostName);
        destprops.setProperty("soap.port", _port + "");
        destprops.setProperty("soap.urlpath", _urlPath);
        destprops.setProperty("soap.envelope", "<ip>@IP</ip>");
        destprops.setProperty("soap.action", "send");
        HashMap<String, String> soapmap = new HashMap<String, String>();
        soapmap.put("IP", "127.0.0.1");
        return new SoapClient(soapmap, destprops);
    }

    /*
     * A connection handler class that verifies the correct message is received
     * 
     */
    class ConnectionHandler implements Runnable
    {
        private Socket socket;

        public ConnectionHandler(Socket socket)
        {
            this.socket = socket;
            Thread t = new Thread(this);
            t.start();
        }

        public void run()
        {
            String line;
            final List<String> response = new ArrayList<String>();
            try
            {
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                assertNotNull(br);
                while ((line = br.readLine()) != null)
                {
                    response.add(line);
                }
                br.close();
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                fail("Exception while reading from the socket");
            }
            assertTrue(response.contains("<ip>127.0.0.1</ip>"));
            assertTrue(response.contains("SOAPAction: \"urn:send\""));
            assertTrue(response
                    .contains("Content-Type: text/xml; charset=\"utf-8\""));
            assertTrue(response.contains("Host: localhost" + _port));
            assertTrue(response.contains("User-Agent: Axis2"));
        }

    }

    /*
     * Test that the SOAP client sends the expected data to the socket We mock a
     * simple SOAP envelope: <ip>127.0.0.1</ip>
     */
    public void testSoapClient() throws Exception
    {
        //
        try
        {
            _server = new ServerSocket(0);
            _port = _server.getLocalPort();
            assertTrue("Server is not yet bound to a port", _port != -1);
            assertNotNull(_server);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            fail("Unable to start the socket server");
        }

        Thread _socketAcceptor = new Thread()
        {
            public void run()
            {
                try
                {
                    Socket socket = _server.accept();
                    new ConnectionHandler(socket);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        };
        _socketAcceptor.start();
        // Sleep for 1 second to allow the ServerSocket readiness
        Thread.sleep(1000);
        SoapClient sc = getSoapClient();
        assertNotNull(sc);
        sc.sendSOAPMessage();

        _socketAcceptor.join(2000);

        assertFalse("Socket Acceptor not stopped.", _socketAcceptor.isAlive());
    }

    /**
     * Test SoapClient correctly clears previously set values
     */
    public void testSoapClientXMLData()
    {
        SoapClient sc = getSoapClient();

        StringBuffer initial = new StringBuffer("Initial Value");

        sc.setXMLData(initial);

        assertEquals("getXMLData is not set with initial value",
                     initial.toString(), sc.getXMLData().toString());


        StringBuffer sb = new StringBuffer("<?xml version=\"1.0\"?><ip=@IP><port=@PORT>");
        sc.setXMLData(sb);
        assertEquals(sc.getXMLData().length(), sb.length());
        assertEquals("getXMLData does not return the same StringBuffer set by setXMLData",
                     sb.toString(), sc.getXMLData().toString());
    }

    /**
     * Test that variable replacement is performed on the soap.envelope.
     * Create dummy soap message and validate that the variable have been replaced.
     */
    public void testReplaceVariablesMap()
    {
        Properties props = new Properties();
        // Add dummy values as required to create a soap message
        props.setProperty("soap.hostname", _hostName);
        props.setProperty("soap.port", "0");
        props.setProperty("soap.urlpath", _urlPath);
        props.setProperty("soap.action", "send");

        /// The envelope is what we care about
        props.setProperty("soap.envelope", "<addr>@IP:@PORT</addr>");
        HashMap<String, String> soapmap = new HashMap<String, String>();

        /// Variables that should be replaced.
        final String ip = "127.0.0.1";
        soapmap.put("IP", ip);
        final String port = "8080";
        soapmap.put("PORT", port);

        SoapClient sc = new SoapClient(soapmap, props);
        assertNotNull("SoapClient is null", sc);

        assertTrue("Replace variables did not work as expected", ("<addr>" + ip + ":" + port + "</addr>").equals(sc.getXMLData().toString()));
    }

}
