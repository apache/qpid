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
package org.apache.qpid.server.protocol.v0_8;

import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;

public class MaxChannelsTest extends QpidTestCase
{
    private AMQProtocolEngine _session;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _session = BrokerTestHelper_0_8.createProtocolSession();
    }

    public void testChannels() throws Exception
    {
        // check the channel count is correct
        int channelCount = _session.getChannels().size();
        assertEquals("Initial channel count wrong", 0, channelCount);

        long maxChannels = 10L;
        _session.setMaximumNumberOfChannels(maxChannels);
        assertEquals("Number of channels not correctly set.", maxChannels, _session.getMaximumNumberOfChannels());

        for (long currentChannel = 1L; currentChannel <= maxChannels; currentChannel++)
        {
            _session.receiveChannelOpen( (int) currentChannel);
        }
        assertFalse("Connection should not be closed after opening " + maxChannels + " channels",_session.isClosed());
        assertEquals("Maximum number of channels not set.", maxChannels, _session.getChannels().size());
        _session.receiveChannelOpen((int) maxChannels+1);
        assertTrue("Connection should be closed after opening " + (maxChannels + 1) + " channels",_session.isClosed());
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            _session.getVirtualHost().close();
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

}
