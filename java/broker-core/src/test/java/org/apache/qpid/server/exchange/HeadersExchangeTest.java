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
package org.apache.qpid.server.exchange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import junit.framework.TestCase;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HeadersExchangeTest extends TestCase
{
    private HeadersExchange _exchange;
    private VirtualHost _virtualHost;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        CurrentActor.setDefault(mock(LogActor.class));
        _virtualHost = mock(VirtualHost.class);
        SecurityManager securityManager = mock(SecurityManager.class);
        when(_virtualHost.getSecurityManager()).thenReturn(securityManager);
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(Exchange.ID, UUID.randomUUID());
        attributes.put(Exchange.NAME, "test");
        attributes.put(Exchange.DURABLE, false);

        _exchange = new HeadersExchange(_virtualHost, attributes);

    }

    protected void routeAndTest(ServerMessage msg, AMQQueue... expected) throws Exception
    {
        List<? extends BaseQueue> results = _exchange.route(msg, InstanceProperties.EMPTY);
        List<? extends BaseQueue> unexpected = new ArrayList<BaseQueue>(results);
        unexpected.removeAll(Arrays.asList(expected));
        assertTrue("Message delivered to unexpected queues: " + unexpected, unexpected.isEmpty());
        List<? extends BaseQueue> missing = new ArrayList<BaseQueue>(Arrays.asList(expected));
        missing.removeAll(results);
        assertTrue("Message not delivered to expected queues: " + missing, missing.isEmpty());
        assertTrue("Duplicates " + results, results.size()==(new HashSet<BaseQueue>(results)).size());
    }


    private AMQQueue createAndBind(final String name, String... arguments)
            throws Exception
    {
        return createAndBind(name, getArgsMapFromStrings(arguments));
    }

    private Map<String, Object> getArgsMapFromStrings(String... arguments)
    {
        Map<String, Object> map = new HashMap<String,Object>();

        for(String arg : arguments)
        {
            if(arg.contains("="))
            {
                String[] keyValue = arg.split("=",2);
                map.put(keyValue[0],keyValue[1]);
            }
            else
            {
                map.put(arg,null);
            }
        }
        return map;
    }

    private AMQQueue createAndBind(final String name, Map<String, Object> arguments)
            throws Exception
    {
        AMQQueue q = create(name);
        bind(name, arguments, q);
        return q;
    }

    private void bind(String bindingKey, Map<String, Object> arguments, AMQQueue q)
    {
        _exchange.addBinding(bindingKey,q,arguments);
    }

    private AMQQueue create(String name)
    {
        AMQQueue q = mock(AMQQueue.class);
        when(q.toString()).thenReturn(name);
        when(q.getVirtualHost()).thenReturn(_virtualHost);
        return q;
    }


    public void testSimple() throws Exception
    {
        AMQQueue q1 = createAndBind("Q1", "F0000");
        AMQQueue q2 = createAndBind("Q2", "F0000=Aardvark");
        AMQQueue q3 = createAndBind("Q3", "F0001");
        AMQQueue q4 = createAndBind("Q4", "F0001=Bear");
        AMQQueue q5 = createAndBind("Q5", "F0000", "F0001");
        AMQQueue q6 = createAndBind("Q6", "F0000=Aardvark", "F0001=Bear");
        AMQQueue q7 = createAndBind("Q7", "F0000", "F0001=Bear");
        AMQQueue q8 = createAndBind("Q8", "F0000=Aardvark", "F0001");

        routeAndTest(mockMessage(getArgsMapFromStrings("F0000")), q1);
        routeAndTest(mockMessage(getArgsMapFromStrings("F0000=Aardvark")), q1, q2);
        routeAndTest(mockMessage(getArgsMapFromStrings("F0000=Aardvark", "F0001")), q1, q2, q3, q5, q8);
        routeAndTest(mockMessage(getArgsMapFromStrings("F0000", "F0001=Bear")), q1, q3, q4, q5, q7);
        routeAndTest(mockMessage(getArgsMapFromStrings("F0000=Aardvark", "F0001=Bear")),
                q1, q2, q3, q4, q5, q6, q7, q8);
        routeAndTest(mockMessage(getArgsMapFromStrings("F0002")));

    }

    public void testAny() throws Exception
    {
        AMQQueue q1 = createAndBind("Q1", "F0000", "F0001", "X-match=any");
        AMQQueue q2 = createAndBind("Q2", "F0000=Aardvark", "F0001=Bear", "X-match=any");
        AMQQueue q3 = createAndBind("Q3", "F0000", "F0001=Bear", "X-match=any");
        AMQQueue q4 = createAndBind("Q4", "F0000=Aardvark", "F0001", "X-match=any");
        AMQQueue q5 = createAndBind("Q5", "F0000=Apple", "F0001", "X-match=any");

        routeAndTest(mockMessage(getArgsMapFromStrings("F0000")), q1, q3);
        routeAndTest(mockMessage(getArgsMapFromStrings("F0000=Aardvark")), q1, q2, q3, q4);
        routeAndTest(mockMessage(getArgsMapFromStrings("F0000=Aardvark", "F0001")), q1, q2, q3, q4, q5);
        routeAndTest(mockMessage(getArgsMapFromStrings("F0000", "F0001=Bear")), q1, q2, q3, q4, q5);
        routeAndTest(mockMessage(getArgsMapFromStrings("F0000=Aardvark", "F0001=Bear")), q1, q2, q3, q4, q5);
        routeAndTest(mockMessage(getArgsMapFromStrings("F0002")));
    }

    public void testOnUnbind() throws Exception
    {
        AMQQueue q1 = createAndBind("Q1", "F0000");
        AMQQueue q2 = createAndBind("Q2", "F0000=Aardvark");
        AMQQueue q3 = createAndBind("Q3", "F0001");

        routeAndTest(mockMessage(getArgsMapFromStrings("F0000")), q1);
        routeAndTest(mockMessage(getArgsMapFromStrings("F0000=Aardvark")), q1, q2);
        routeAndTest(mockMessage(getArgsMapFromStrings("F0001")), q3);

        _exchange.deleteBinding("Q1",q1);

        routeAndTest(mockMessage(getArgsMapFromStrings("F0000")));
        routeAndTest(mockMessage(getArgsMapFromStrings("F0000=Aardvark")), q2);
    }


    public void testWithSelectors() throws Exception
    {
        AMQQueue q1 = create("Q1");
        AMQQueue q2 = create("Q2");
        bind("q1",getArgsMapFromStrings("F"), q1);
        bind("q1select",getArgsMapFromStrings("F", AMQPFilterTypes.JMS_SELECTOR.toString()+"=F='1'"), q1);
        bind("q2",getArgsMapFromStrings("F=1"), q2);

        routeAndTest(mockMessage(getArgsMapFromStrings("F")),q1);

        routeAndTest(mockMessage(getArgsMapFromStrings("F=1")),q1,q2);


        AMQQueue q3 = create("Q3");
        bind("q3select",getArgsMapFromStrings("F", AMQPFilterTypes.JMS_SELECTOR.toString()+"=F='1'"), q3);
        routeAndTest(mockMessage(getArgsMapFromStrings("F=1")),q1,q2,q3);
        routeAndTest(mockMessage(getArgsMapFromStrings("F=2")),q1);
        bind("q3select2",getArgsMapFromStrings("F", AMQPFilterTypes.JMS_SELECTOR.toString()+"=F='2'"), q3);

        routeAndTest(mockMessage(getArgsMapFromStrings("F=2")),q1,q3);

    }

    private ServerMessage mockMessage(final Map<String, Object> headerValues)
    {
        final AMQMessageHeader header = mock(AMQMessageHeader.class);
        when(header.containsHeader(anyString())).then(new Answer<Boolean>()
        {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable
            {
                return headerValues.containsKey((String) invocation.getArguments()[0]);
            }
        });
        when(header.getHeader(anyString())).then(new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable
            {
                return headerValues.get((String) invocation.getArguments()[0]);
            }
        });
        when(header.getHeaderNames()).thenReturn(headerValues.keySet());
        when(header.containsHeaders(anySet())).then(new Answer<Boolean>()
        {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable
            {
                final Set names = (Set) invocation.getArguments()[0];
                return headerValues.keySet().containsAll(names);

            }
        });
        final ServerMessage serverMessage = mock(ServerMessage.class);
        when(serverMessage.getMessageHeader()).thenReturn(header);
        return serverMessage;
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(HeadersExchangeTest.class);
    }
}
