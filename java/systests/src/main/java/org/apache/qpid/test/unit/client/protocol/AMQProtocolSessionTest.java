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
package org.apache.qpid.test.unit.client.protocol;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.transport.TestNetworkDriver;

public class AMQProtocolSessionTest extends QpidBrokerTestCase
{
    private static class AMQProtSession extends AMQProtocolSession
    {

        public AMQProtSession(AMQProtocolHandler protocolHandler, AMQConnection connection)
        {
            super(protocolHandler,connection);
        }

        public TestNetworkDriver getNetworkDriver()
        {
            return (TestNetworkDriver) _protocolHandler.getNetworkDriver();
        }

        public AMQShortString genQueueName()
        {
            return generateQueueName();
        }
    }

    private AMQProtSession _testSession;

    protected void setUp() throws Exception
    {
        super.setUp();

        AMQConnection con = (AMQConnection) getConnection("guest", "guest");
        AMQProtocolHandler protocolHandler = new AMQProtocolHandler(con);
        protocolHandler.setNetworkDriver(new TestNetworkDriver());
        
        //don't care about the values set here apart from the dummy IoSession
        _testSession = new AMQProtSession(protocolHandler , con);
    }
    
    public void testTemporaryQueueWildcard() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(1234), "tmp_0_0_0_0_0_0_0_0_1234_1");
    }
    
    public void testTemporaryQueueLocalhostAddr() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 1234), "tmp_127_0_0_1_1234_1");
    }
    
    public void testTemporaryQueueLocalhostName() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(InetAddress.getByName("localhost"), 1234), "tmp_localhost_127_0_0_1_1234_1");
    }
    
    public void testTemporaryQueueInet4() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(InetAddress.getByName("192.168.1.2"), 1234), "tmp_192_168_1_2_1234_1");
    }
    
    public void testTemporaryQueueInet6() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(InetAddress.getByName("1080:0:0:0:8:800:200C:417A"), 1234), "tmp_1080_0_0_0_8_800_200c_417a_1234_1");
    }
    
    public void testTemporaryQueuePipe() throws UnknownHostException
    {
        checkTempQueueName(new VmPipeAddress(1), "tmp_vm_1_1");
    }
    
    private void checkTempQueueName(SocketAddress address, String queueName)
    {
        _testSession.getNetworkDriver().setLocalAddress(address);
        assertEquals("Wrong queue name", queueName, _testSession.genQueueName().asString());
    }
}
