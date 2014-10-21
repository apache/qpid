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

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.message.MessageContentSource;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.test.utils.QpidTestCase;

public class AMQChannelTest extends QpidTestCase
{
    private VirtualHostImpl _virtualHost;
    private AMQProtocolEngine _protocolSession;
    private Map<Integer,String> _replies;
    private Broker _broker;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _virtualHost = BrokerTestHelper.createVirtualHost(getTestName());
        _broker = BrokerTestHelper.createBrokerMock();
        AmqpPort port = mock(AmqpPort.class);
        when(port.getContextValue(eq(Integer.class), eq(AmqpPort.PORT_MAX_MESSAGE_SIZE))).thenReturn(AmqpPort.DEFAULT_MAX_MESSAGE_SIZE);

        _protocolSession = new InternalTestProtocolSession(_virtualHost, _broker, port)
        {
            @Override
            public void writeReturn(MessagePublishInfo messagePublishInfo,
                    ContentHeaderBody header,
                    MessageContentSource msgContent,
                    int channelId,
                    int replyCode,
                    AMQShortString replyText)
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

        AmqpPort port = mock(AmqpPort.class);
        when(port.getContextValue(eq(Integer.class), eq(AmqpPort.PORT_MAX_MESSAGE_SIZE))).thenReturn(AmqpPort.DEFAULT_MAX_MESSAGE_SIZE);
        // create a channel with the same channelId but on a different session
        AMQChannel channel2 = new AMQChannel(new InternalTestProtocolSession(_virtualHost, _broker, port), 1, _virtualHost.getMessageStore());
        assertFalse("Unexpected compare result", channel1.compareTo(channel2) == 0);
        assertEquals("Unexpected compare result", 0, channel1.compareTo(channel1));
    }

    public void testPublishContentHeaderWhenMessageAuthorizationFails() throws Exception
    {
        setTestSystemProperty(BrokerProperties.PROPERTY_MSG_AUTH, "true");
        AMQChannel channel = new AMQChannel(_protocolSession, 1, _virtualHost.getMessageStore());
        channel.setLocalTransactional();

        MessagePublishInfo info = new MessagePublishInfo(new AMQShortString("test"), false, false, null);
        ExchangeImpl e = mock(ExchangeImpl.class);
        ContentHeaderBody contentHeaderBody= mock(ContentHeaderBody.class);
        BasicContentHeaderProperties properties = mock(BasicContentHeaderProperties.class);

        when(contentHeaderBody.getProperties()).thenReturn(properties);
        when(properties.getUserId()).thenReturn(new AMQShortString(_protocolSession.getAuthorizedPrincipal().getName() + "_incorrect"));

        channel.setPublishFrame(info, e);
        channel.publishContentHeader(contentHeaderBody);
        channel.commit(null, false);

        assertEquals("Unexpected number of replies", 1, _replies.size());
        assertEquals("Message authorization passed", "Access Refused", _replies.get(403));
    }

    public void testPublishContentHeaderWhenMessageAuthorizationPasses() throws Exception
    {
        setTestSystemProperty(BrokerProperties.PROPERTY_MSG_AUTH, "true");
        AMQChannel channel = new AMQChannel(_protocolSession, 1, _virtualHost.getMessageStore());
        channel.setLocalTransactional();

        MessagePublishInfo info = new MessagePublishInfo(new AMQShortString("test"), false, false, null);
        ExchangeImpl e = mock(ExchangeImpl.class);
        ContentHeaderBody contentHeaderBody= mock(ContentHeaderBody.class);
        BasicContentHeaderProperties properties = mock(BasicContentHeaderProperties.class);

        when(contentHeaderBody.getProperties()).thenReturn(properties);
        when(properties.getUserId()).thenReturn(new AMQShortString(_protocolSession.getAuthorizedPrincipal().getName()));

        channel.setPublishFrame(info, e);
        channel.publishContentHeader(contentHeaderBody);
        channel.commit(null, false);

        assertEquals("Unexpected number of replies", 0, _replies.size());
    }

    public void testOverlargeMessage() throws Exception
    {

        AmqpPort port = mock(AmqpPort.class);
        when(port.getContextValue(eq(Integer.class), eq(AmqpPort.PORT_MAX_MESSAGE_SIZE))).thenReturn(1024);
        final List<AMQDataBlock> frames = new ArrayList<>();
        _protocolSession = new InternalTestProtocolSession(_virtualHost, _broker, port)
        {
            @Override
            public synchronized void writeFrame(final AMQDataBlock frame)
            {
                frames.add(frame);
            }
        };

        AMQChannel channel = new AMQChannel(_protocolSession, 1, _virtualHost.getMessageStore());

        channel.receiveBasicPublish(AMQShortString.EMPTY_STRING, AMQShortString.EMPTY_STRING, false, false);

        final BasicContentHeaderProperties properties = new BasicContentHeaderProperties();
        channel.receiveMessageHeader(properties, 2048l);

        frames.toString();

        assertEquals(1, frames.size());
        assertEquals(ChannelCloseBody.class, ((AMQFrame) frames.get(0)).getBodyFrame().getClass());
        assertEquals(AMQConstant.MESSAGE_TOO_LARGE.getCode(), ((ChannelCloseBody)((AMQFrame)frames.get(0)).getBodyFrame()).getReplyCode());
    }

}
