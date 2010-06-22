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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import junit.framework.TestCase;

import org.apache.qpid.info.util.SoapClient;

public class SoapClientTest extends TestCase
{

    private final int port = 9900;

    private final String hostName = "localhost";

    private final String urlPath = "/testSoap";

    private ServerSocket server = null;

    Thread socketAcceptor;

    /*
     * Generate a soap client from a custom URL, hostname, port and soap context
     * path to be derived
     */
    private SoapClient getSoapClient()
    {
        Properties destprops = new Properties();
        destprops.setProperty("soap.hostname", hostName);
        destprops.setProperty("soap.port", port + "");
        destprops.setProperty("soap.urlpath", urlPath);
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
            } catch (Exception ex)
            {
                ex.printStackTrace();
                fail("Exception while reading from the socket");
            }
            assertTrue(response.contains("<ip>127.0.0.1</ip>"));
            assertTrue(response.contains("SOAPAction: \"urn:send\""));
            assertTrue(response
                    .contains("Content-Type: text/xml; charset=\"utf-8\""));
            assertTrue(response.contains("Host: localhost:9000"));
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
            server = new ServerSocket(port);
            assertNotNull(server);
        } catch (Exception ex)
        {
            ex.printStackTrace();
            fail("Unable to start the socket server");
        }

        socketAcceptor = new Thread()
        {
            public void run()
            {
                try
                {
                    Socket socket = server.accept();
                    new ConnectionHandler(socket);
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        };
        socketAcceptor.start();
        // Sleep for 1 second to allow the ServerSocket readiness
        Thread.sleep(1000);
        SoapClient sc = getSoapClient();
        assertNotNull(sc);
        sc.sendSOAPMessage();
        socketAcceptor.join(2000);
    }

    public void testSoapClientFailure() throws Exception
    {
        SoapClient sc = new SoapClient(null, null);
        assertNull("No response expected for the failure test", sc
                .sendSOAPMessage());
    }

}
