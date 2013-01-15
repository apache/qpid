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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.message.MessageContentSource;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class AMQChannelTest extends QpidTestCase
{
    private VirtualHost _virtualHost;
    private AMQProtocolSession _protocolSession;
    private Map<Integer,String> _replies;
    private Broker _broker;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _virtualHost = BrokerTestHelper.createVirtualHost(getTestName());
        _broker = BrokerTestHelper.createBrokerMock();
        _protocolSession = new InternalTestProtocolSession(_virtualHost, _broker)
        {
            @Override
            public void writeReturn(MessagePublishInfo messagePublishInfo,
                    ContentHeaderBody header,
                    MessageContentSource msgContent,
                    int channelId,
                    int replyCode,
                    AMQShortString replyText) throws AMQException
                    {
                        _replies.put(replyCode, replyText.asString());
                    }
        };
        _replies = new HashMap<Integer, String>();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            _virtualHost.close();
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    public void testCompareTo() throws Exception
    {
        AMQChannel channel1 = new AMQChannel(_protocolSession, 1, _virtualHost.getMessageStore());

        // create a channel with the same channelId but on a different session
        AMQChannel channel2 = new AMQChannel(new InternalTestProtocolSession(_virtualHost, _broker), 1, _virtualHost.getMessageStore());
        assertFalse("Unexpected compare result", channel1.compareTo(channel2) == 0);
        assertEquals("Unexpected compare result", 0, channel1.compareTo(channel1));
    }

    public void testPublishContentHeaderWhenMessageAuthorizationFails() throws Exception
    {
        setTestSystemProperty(BrokerProperties.PROPERTY_MSG_AUTH, "true");
        AMQChannel channel = new AMQChannel(_protocolSession, 1, _virtualHost.getMessageStore());
        channel.setLocalTransactional();

        MessagePublishInfo info = mock(MessagePublishInfo.class);
        Exchange e = mock(Exchange.class);
        ContentHeaderBody contentHeaderBody= mock(ContentHeaderBody.class);
        BasicContentHeaderProperties properties = mock(BasicContentHeaderProperties.class);

        when(contentHeaderBody.getProperties()).thenReturn(properties);
        when(info.getExchange()).thenReturn(new AMQShortString("test"));
        when(properties.getUserId()).thenReturn(new AMQShortString(_protocolSession.getAuthorizedPrincipal().getName() + "_incorrect"));

        channel.setPublishFrame(info, e);
        channel.publishContentHeader(contentHeaderBody);
        channel.commit();

        assertEquals("Unexpected number of replies", 1, _replies.size());
        assertEquals("Message authorization passed", "Access Refused", _replies.get(403));
    }

    public void testPublishContentHeaderWhenMessageAuthorizationPasses() throws Exception
    {
        setTestSystemProperty(BrokerProperties.PROPERTY_MSG_AUTH, "true");
        AMQChannel channel = new AMQChannel(_protocolSession, 1, _virtualHost.getMessageStore());
        channel.setLocalTransactional();

        MessagePublishInfo info = mock(MessagePublishInfo.class);
        Exchange e = mock(Exchange.class);
        ContentHeaderBody contentHeaderBody= mock(ContentHeaderBody.class);
        BasicContentHeaderProperties properties = mock(BasicContentHeaderProperties.class);

        when(contentHeaderBody.getProperties()).thenReturn(properties);
        when(info.getExchange()).thenReturn(new AMQShortString("test"));
        when(properties.getUserId()).thenReturn(new AMQShortString(_protocolSession.getAuthorizedPrincipal().getName()));

        channel.setPublishFrame(info, e);
        channel.publishContentHeader(contentHeaderBody);
        channel.commit();

        assertEquals("Unexpected number of replies", 0, _replies.size());
    }

}
