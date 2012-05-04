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
package org.apache.qpid.server;

import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.InternalBrokerBaseCase;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class AMQChannelTest extends InternalBrokerBaseCase
{
    private VirtualHost _virtualHost;
    private AMQProtocolSession _protocolSession;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _virtualHost = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHosts().iterator().next();
        _protocolSession = new InternalTestProtocolSession(_virtualHost);
    }

    public void testCompareTo() throws Exception
    {
        AMQChannel channel1 = new AMQChannel(_protocolSession, 1, _virtualHost.getMessageStore());

        // create a channel with the same channelId but on a different session
        AMQChannel channel2 = new AMQChannel(new InternalTestProtocolSession(_virtualHost), 1, _virtualHost.getMessageStore());
        assertFalse("Unexpected compare result", channel1.compareTo(channel2) == 0);
        assertEquals("Unexpected compare result", 0, channel1.compareTo(channel1));
    }

}
