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

package org.apache.qpid.transport.network.mina;

import static org.apache.qpid.transport.ConnectionSettings.WILDCARD_ADDRESS;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.NetworkTransportConfiguration;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.IncomingNetworkTransport;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.OutgoingNetworkTransport;
import org.apache.qpid.transport.network.Transport;

public class MinaNetworkHandlerTest extends QpidTestCase
{
 
    private static final String TEST_DATA = "YHALOTHAR";
    private int _testPort;
    private IncomingNetworkTransport _server;
    private OutgoingNetworkTransport _client;
    private CountingProtocolEngine _countingEngine; // Keeps a count of how many bytes it's read
    private Exception _thrownEx;
    private ConnectionSettings _clientSettings;
    private NetworkConnection _network;
    private TestNetworkTransportConfiguration _brokerSettings;

    @Override
    public void setUp() throws Exception
    {
        String host = InetAddress.getLocalHost().getHostName();
        _testPort = findFreePort();

        _clientSettings = new ConnectionSettings();
        _clientSettings.setHost(host);
        _clientSettings.setPort(_testPort);

        _brokerSettings = new TestNetworkTransportConfiguration(_testPort, host);

        _server = new MinaNetworkTransport();
        _client = new MinaNetworkTransport();
        _thrownEx = null;
        _countingEngine = new CountingProtocolEngine();
    }

    @Override
    public void tearDown()
    {
        if (_server != null)
        {
            _server.close();
        }

        if (_client != null)
        {
            _client.close();
        }
    }
    
    /**
     * Tests that a socket can't be opened if a driver hasn't been bound
     * to the port and can be opened if a driver has been bound.
     */
    public void testBindOpen() throws Exception
    {
        try
        {
            _client.connect(_clientSettings, _countingEngine, null);
        } 
        catch (TransportException e)
        {
            _thrownEx = e;
        }

        assertNotNull("Open should have failed since no engine bound", _thrownEx);        
        
        _server.accept(_brokerSettings, null, null);
        
        _client.connect(_clientSettings, _countingEngine, null);
    } 
    
    /**
     * Tests that a socket can't be opened after a bound NetworkDriver has been closed
     */
    public void testBindOpenCloseOpen() throws Exception
    {
        _server.accept(_brokerSettings, new EchoProtocolEngineSingletonFactory(), null);
        _client.connect(_clientSettings, _countingEngine, null);
        _client.close();
        _server.close();
        
        try
        {
            _client.connect(_clientSettings, _countingEngine, null);
        } 
        catch (TransportException e)
        {
            _thrownEx = e;
        }
        assertNotNull("Open should have failed", _thrownEx);
    }
    
    /**
     * Checks that the right exception is thrown when binding a NetworkDriver to an already
     * existing socket. 
     */
    public void testBindPortInUse() 
    {
        try
        {
            _server.accept(_brokerSettings, new EchoProtocolEngineSingletonFactory(), null);
        }
        catch (TransportException e)
        {
            fail("First bind should not fail");
        }
        
        try
        {
            IncomingNetworkTransport second = new MinaNetworkTransport();
            second.accept(_brokerSettings, new EchoProtocolEngineSingletonFactory(), null);
        }
        catch (TransportException e)
        {
            _thrownEx = e;
        }
        assertNotNull("Second bind should throw BindException", _thrownEx);
    }

    /**
     * Tests that binding to the wildcard address succeeds and a client can
     * connect via localhost.
     */
    public void testWildcardBind() throws Exception
    {
        TestNetworkTransportConfiguration serverSettings = 
            new TestNetworkTransportConfiguration(_testPort, WILDCARD_ADDRESS);

        _server.accept(serverSettings, null, null);

        try
        {
            _client.connect(_clientSettings, _countingEngine, null);
        } 
        catch (TransportException e)
        {
            fail("Open should have succeeded since we used a wildcard bind");        
        }
    }

    /**
     * tests that bytes sent on a network driver are received at the other end
     */
    public void testSend() throws Exception 
    {
        // Open a connection from a counting engine to an echo engine
        _server.accept(_brokerSettings, new EchoProtocolEngineSingletonFactory(), null);
        _network = _client.connect(_clientSettings, _countingEngine, null);
        
        // Tell the counting engine how much data we're sending
        _countingEngine.setNewLatch(TEST_DATA.getBytes().length);
        
        // Send the data and wait for up to 2 seconds to get it back 
        _network.getSender().send(ByteBuffer.wrap(TEST_DATA.getBytes()));
        _countingEngine.getLatch().await(2, TimeUnit.SECONDS);
        
        // Check what we got
        assertEquals("Wrong amount of data recieved", TEST_DATA.getBytes().length, _countingEngine.getReadBytes());
    } 
    
    /**
     * Opens a connection with a low read idle and check that it gets triggered
     * 
     */
    public void testSetReadIdle() throws Exception
    {
        // Open a connection from a counting engine to an echo engine
        _server.accept(_brokerSettings, new EchoProtocolEngineSingletonFactory(), null);
        _network = _client.connect(_clientSettings, _countingEngine, null);
        assertFalse("Reader should not have been idle", _countingEngine.getReaderHasBeenIdle());
        _network.setMaxReadIdle(1);
        sleepForAtLeast(1500);
        assertTrue("Reader should have been idle", _countingEngine.getReaderHasBeenIdle());
    } 
    
    /**
     * Opens a connection with a low write idle and check that it gets triggered
     * 
     */
    public void testSetWriteIdle() throws Exception
    {
        // Open a connection from a counting engine to an echo engine
        _server.accept(_brokerSettings, new EchoProtocolEngineSingletonFactory(), null);
        _network = _client.connect(_clientSettings, _countingEngine, null);
        assertFalse("Reader should not have been idle", _countingEngine.getWriterHasBeenIdle());
        _network.setMaxWriteIdle(1);
        sleepForAtLeast(1500);
        assertTrue("Reader should have been idle", _countingEngine.getWriterHasBeenIdle());
    } 
    
    
    /**
     * Creates and then closes a connection from client to server and checks that the server
     * has its closed() method called. Then creates a new client and closes the server to check
     * that the client has its closed() method called.  
     */
    public void testClosed() throws Exception
    {
        // Open a connection from a counting engine to an echo engine
        EchoProtocolEngineSingletonFactory factory = new EchoProtocolEngineSingletonFactory();
        _server.accept(_brokerSettings, factory, null);
        _network = _client.connect(_clientSettings, _countingEngine, null);
        EchoProtocolEngine serverEngine = null; 
        while (serverEngine == null)
        {
            serverEngine = factory.getEngine();
            if (serverEngine == null)
            {
                try
                {
                    Thread.sleep(10);
                }
                catch (InterruptedException e)
                {
                }
            }
        }
        assertFalse("Server should not have been closed", serverEngine.getClosed());
        serverEngine.setNewLatch(1);
        _client.close();
        try
        {
            serverEngine.getLatch().await(2, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
        }
        assertTrue("Server should have been closed", serverEngine.getClosed());

        _client.connect(_clientSettings, _countingEngine, null);
        _countingEngine.setClosed(false);
        assertFalse("Client should not have been closed", _countingEngine.getClosed());
        _countingEngine.setNewLatch(1);
        _server.close();
        try
        {
            _countingEngine.getLatch().await(2, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
        }
        assertTrue("Client should have been closed", _countingEngine.getClosed());
    } 

    /**
     * Create a connection and instruct the client to throw an exception when it gets some data
     * and that the latch gets counted down. 
     */
    public void testExceptionCaught() throws Exception
    {
        _server.accept(_brokerSettings, new EchoProtocolEngineSingletonFactory(), null);
        _network = _client.connect(_clientSettings, _countingEngine, null);


        assertEquals("Exception should not have been thrown", 1, 
                _countingEngine.getExceptionLatch().getCount());
        _countingEngine.setErrorOnNextRead(true);
        _countingEngine.setNewLatch(TEST_DATA.getBytes().length);
        _network.getSender().send(ByteBuffer.wrap(TEST_DATA.getBytes()));
        _countingEngine.getExceptionLatch().await(2, TimeUnit.SECONDS);
        assertEquals("Exception should have been thrown", 0, 
                _countingEngine.getExceptionLatch().getCount());
    } 
    
    /**
     * Opens a connection and checks that the remote address is the one that was asked for
     */
    public void testGetRemoteAddress() throws Exception
    {
        _server.accept(_brokerSettings, new EchoProtocolEngineSingletonFactory(), null);
        _network = _client.connect(_clientSettings, _countingEngine, null);
        assertEquals(new InetSocketAddress(InetAddress.getLocalHost(), _testPort),
                     _network.getRemoteAddress());
    }

    private class EchoProtocolEngineSingletonFactory implements ProtocolEngineFactory
    {
        private EchoProtocolEngine _engine = null;
        
        public ProtocolEngine newProtocolEngine(NetworkConnection network)
        {
            if (_engine == null)
            {
                _engine = new EchoProtocolEngine(network);
            }
            return getEngine();
        }
        
        public EchoProtocolEngine getEngine()
        {
            return _engine;
        }
    }
    
    public class CountingProtocolEngine implements ProtocolEngine
    {
        public ArrayList<ByteBuffer> _receivedBytes = new ArrayList<ByteBuffer>();
        private int _readBytes;
        private CountDownLatch _latch = new CountDownLatch(0);
        private boolean _readerHasBeenIdle;
        private boolean _writerHasBeenIdle;
        private boolean _closed = false;
        private boolean _nextReadErrors = false;
        private CountDownLatch _exceptionLatch = new CountDownLatch(1);
        
        public void closed()
        {
            setClosed(true);
            _latch.countDown();
        }

        public void setErrorOnNextRead(boolean b)
        {
            _nextReadErrors = b;
        }

        public void setNewLatch(int length)
        {
            _latch = new CountDownLatch(length);
        }

        public long getReadBytes()
        {
            return _readBytes;
        }

        public SocketAddress getRemoteAddress()
        {
            return _network.getRemoteAddress();
        }
        
        public SocketAddress getLocalAddress()
        {            
            return _network.getLocalAddress();
        }

        public long getWrittenBytes()
        {
            return 0;
        }

        public void readerIdle()
        {
            _readerHasBeenIdle = true;
        }

        public void writeFrame(AMQDataBlock frame)
        {
            
        }

        public void writerIdle()
        {
           _writerHasBeenIdle = true;
        }

        public void exception(Throwable t)
        {
            _exceptionLatch.countDown();
        }

        public CountDownLatch getExceptionLatch()
        {
            return _exceptionLatch;
        }
        
        public void received(ByteBuffer msg)
        {
            // increment read bytes and count down the latch for that many
            int bytes = msg.remaining();
            _readBytes += bytes;
            for (int i = 0; i < bytes; i++)
            {
                _latch.countDown();
            }
            
            // Throw an error if we've been asked too, but we can still count
            if (_nextReadErrors)
            {
                throw new RuntimeException("Was asked to error");
            }
        }
        
        public CountDownLatch getLatch()
        {
            return _latch;
        }

        public boolean getWriterHasBeenIdle()
        {
            return _writerHasBeenIdle;
        }

        public boolean getReaderHasBeenIdle()
        {
            return _readerHasBeenIdle;
        }

        public void setClosed(boolean _closed)
        {
            this._closed = _closed;
        }

        public boolean getClosed()
        {
            return _closed;
        }

    }

    private class EchoProtocolEngine extends CountingProtocolEngine
    {
        private NetworkConnection _echoNetwork;

        public EchoProtocolEngine(NetworkConnection network)
        {
            _echoNetwork = network;
        }

        public void received(ByteBuffer msg)
        {
            super.received(msg);
            msg.rewind();
            _echoNetwork.getSender().send(msg);
        }
    }
    
    public static void sleepForAtLeast(long period)
    {
        long start = System.currentTimeMillis();
        long timeLeft = period;
        while (timeLeft > 0)
        {
            try
            {
                Thread.sleep(timeLeft);
            }
            catch (InterruptedException e)
            {
                // Ignore it
            }
            timeLeft = period - (System.currentTimeMillis() - start);
        }
    }

    private static class TestNetworkTransportConfiguration implements NetworkTransportConfiguration
    {
        private int _port;
        private String _host;

        public TestNetworkTransportConfiguration(final int port, final String host)
        {
            _port = port;
            _host = host;
        }

        public Boolean getTcpNoDelay()
        {
            return true;
        }

        public Integer getReceiveBufferSize()
        {
            return 32768;
        }

        public Integer getSendBufferSize()
        {
            return 32768;
        }

        public Integer getPort()
        {
            return _port;
        }

        public String getHost()
        {
            return _host;
        }

        public String getTransport()
        {
            return Transport.TCP;
        }

        public Integer getConnectorProcessors()
        {
            return 4;
        }
        
    }
}