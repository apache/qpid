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
package org.apache.qpid.info.systest;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class InfoPluginTest extends QpidBrokerTestCase
{
    private String QPID_HOME = null;

    private ServerSocket _server = null;

    private int _port;

    private static final String CR = System.getProperty("line.separator");

    private static final String FS = File.separator;

    private final String _cfgRelPath = "etc" + FS + "qpidinfo.ini";

    private File _tmpCfgFile;

    private final String _soapEnvelopeHead = "<?xml version=\"1.0\"?><soap:Envelope xmlns:soap=\"http://www.w3.org/2001/12/soap-envelope\" soap:encodingStyle=\"http://www.w3.org/2001/12/soap-encoding\">";

    private final String _soapEnvelopeTail = "</soap:Envelope>";

    private String _soapMessage1 = "@ACTION" + "-" + "@VERSION";

    private String _soapMessage2 = "@VERSION" + "-" + "@ACTION";

    private CountDownLatch _latch = new CountDownLatch(2);

    final List<List<String>> _recv = new ArrayList<List<String>>();

    Thread _socketAcceptor;

    public void setUp() throws Exception
    {
        QPID_HOME = System.getProperty("QPID_HOME");
        if (QPID_HOME != null)
        {
            System.out.println("QPID_HOME=" + QPID_HOME);
        }
        else
        {
            fail("QPID_HOME not set");
        }

        startSoapServer();
        // Must start the server first to identify a free port.
        createConfigFile();
    }

    public void tearDown() throws Exception
    {
        System.out.println("*** Stopping socket server...");
        _socketAcceptor.join(2000);

        System.out.println("*** Deleting the config file...");
        if (_tmpCfgFile.isFile())
        {
            _tmpCfgFile.delete();
        }
        super.tearDown();
    }

    private void createConfigFile()
    {
        try
        {
            _tmpCfgFile = new File(QPID_HOME + FS + _cfgRelPath);
            _tmpCfgFile.deleteOnExit();
            if (_tmpCfgFile.isFile())
            {
                _tmpCfgFile.delete();
            }
            assertTrue("Unable to create file.", _tmpCfgFile.createNewFile());
            assertTrue(_tmpCfgFile.isFile());
            FileWriter fwriter = new FileWriter(_tmpCfgFile);
            BufferedWriter writer = new BufferedWriter(fwriter);
            writer.write("protocol=soap");
            writer.write(CR);
            writer.write("soap.hostname=localhost");
            writer.write(CR);
            writer.write("soap.port=" + _port);
            writer.write(CR);
            writer.write(CR);
            writer.write("[MSG1]");
            writer.write(CR);
            writer.write("soap.path=/info1");
            writer.write(CR);
            writer.write("soap.action=submitinfo1");
            writer.write(CR);
            writer.write("soap.envelope=" + _soapEnvelopeHead + _soapMessage1
                         + _soapEnvelopeTail);
            writer.write(CR);
            writer.write(CR);
            writer.write("[MSG2]");
            writer.write(CR);
            writer.write("soap.path=/info2");
            writer.write(CR);
            writer.write("soap.action=submitinfo2");
            writer.write(CR);
            writer.write("soap.envelope=" + _soapEnvelopeHead + _soapMessage2
                         + _soapEnvelopeTail);
            writer.write(CR);
            writer.write(CR);
            writer.close();
            assertTrue("Config file size is zero", _tmpCfgFile.length() > 0);
        }
        catch (IOException e)
        {
            fail("Unable to create the qpidinfo.properties due to: "
                 + e.getMessage());
        }
    }

    private void startSoapServer() throws Exception
    {
        try
        {
            _server = new ServerSocket(0);
            _port = _server.getLocalPort();
            assertTrue("Server not yet bound.", _port != -1);

            assertNotNull("SocketServer is null", _server);
        }
        catch (Exception ex)
        {
            fail("Unable to start the socket server due to: " + ex.getMessage());
        }

        _socketAcceptor = new Thread()
        {
            public void run()
            {
                while (true)
                {
                    try
                    {
                        Socket socket = _server.accept();
                        new ConnectionHandler(socket);
                    }
                    catch (IOException e)
                    {
                        fail("Error opening the socket in accept mode");
                    }
                }
            }
        };
        _socketAcceptor.start();
        System.out.println("*** Socket server started...");
    }

    class ConnectionHandler implements Runnable
    {
        private Socket _socket;

        public ConnectionHandler(Socket socket)
        {
            _socket = socket;
            Thread t = new Thread(this);
            t.start();
        }

        public void run()
        {
            System.out.println("*** Connection handler running...");
            List<String> buf = new ArrayList<String>();
            String line;
            try
            {
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        _socket.getInputStream()));
                assertNotNull(br);
                while ((line = br.readLine()) != null)
                {
                    buf.add(line);
                }
                br.close();
                System.out.println("*** Received buffer: " + buf);
                System.out.println("*** Latch countdown");
                _latch.countDown();
                synchronized (_recv)
                {
                    _recv.add(buf);
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                fail("Exception while reading from the socket");
            }

        }

    }

    public void testInfoPlugin() throws Exception
    {
        //Start the broker
        super.setUp();
        if (!_latch.await(10, TimeUnit.SECONDS))
        {
            fail("Timeout awaiting for the latch, upon startup");
        }

        validateResponses("STARTUP");

        _recv.clear();
        _latch = new CountDownLatch(2);

        stopBroker();

        if (!_latch.await(10, TimeUnit.SECONDS))
        {
            fail("Timeout awaiting for the latch, upon shutdown");
        }

        validateResponses("SHUTDOWN");

    }

    /**
     * Check the responses from the server to ensure they contain the required messages.
     * @param action String to match for the SHUTDOWN or STARTUP action.
     */
    private void validateResponses(String action)
    {
        assertTrue("Received less than 2 messages", _recv.size() > 1);

        // Message 1
        assertTrue("Message does not contain Host: localhost:" + _port + "\n" + _recv.get(0), _recv.get(0).contains("Host: localhost:" + _port));
        assertTrue("Message does not contain: User-Agent: Axis2 " + "\n" + _recv.get(0), _recv.get(0).contains("User-Agent: Axis2"));
        assertTrue("Message does not contain: SOAPAction: \"urn:submitinfo\"" + "\n" + _recv.get(0).get(4), _recv.get(0).get(4).startsWith("SOAPAction: \"urn:submitinfo"));
        assertTrue("Message does not contain '" + action + "' in the soap envelope" + "\n" + _recv.get(0).get(7), _recv.get(0).get(7).contains(action));

        // Message 2
        assertTrue("Message does not contain Host: localhost:" + _port + "\n" + _recv.get(1), _recv.get(1).contains("Host: localhost:" + _port));
        assertTrue("Message does not contain: User-Agent: Axis2 " + "\n" + _recv.get(1), _recv.get(1).contains("User-Agent: Axis2"));
        assertTrue("Message does not contain: SOAPAction: \"urn:submitinfo\"" + "\n" + _recv.get(1).get(4), _recv.get(1).get(4).startsWith("SOAPAction: \"urn:submitinfo"));
        assertTrue("Message does not contain '" + action + "' in the soap envelope" + "\n" + _recv.get(1).get(7), _recv.get(1).get(7).contains(action));
    }

}
