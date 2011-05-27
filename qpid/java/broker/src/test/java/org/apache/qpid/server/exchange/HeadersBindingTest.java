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

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import junit.framework.TestCase;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.queue.MockAMQQueue;

/**
 */
public class HeadersBindingTest extends TestCase
{

    private class MockHeader implements AMQMessageHeader
    {

        private final Map<String, Object> _headers = new HashMap<String, Object>();

        public String getCorrelationId()
        {
            return null;
        }

        public long getExpiration()
        {
            return 0;
        }

        public String getMessageId()
        {
            return null;
        }

        public String getMimeType()
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public String getEncoding()
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public byte getPriority()
        {
            return 0;
        }

        public long getTimestamp()
        {
            return 0;
        }

        public String getType()
        {
            return null;
        }

        public String getReplyTo()
        {
            return null;
        }

        public String getReplyToExchange()
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public String getReplyToRoutingKey()
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Object getHeader(String name)
        {
            return _headers.get(name);
        }

        public boolean containsHeaders(Set<String> names)
        {
            return _headers.keySet().containsAll(names);
        }

        public boolean containsHeader(String name)
        {
            return _headers.containsKey(name);
        }

        public void setString(String key, String value)
        {
            setObject(key,value);
        }

        public void setObject(String key, Object value)
        {
            _headers.put(key,value);
        }
    }

    private Map<String,Object> bindHeaders = new HashMap<String,Object>();
    private MockHeader matchHeaders = new MockHeader();
    private int _count = 0;
    private MockAMQQueue _queue;
    
    protected void setUp()
    {
        _count++;
        _queue = new MockAMQQueue(getQueueName());
    }
    
    protected String getQueueName()
    {
        return "Queue" + _count;
    }

    public void testDefault_1()
    {
        bindHeaders.put("A", "Value of A");

        matchHeaders.setString("A", "Value of A");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertTrue(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testDefault_2()
    {
        bindHeaders.put("A", "Value of A");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Value of B");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertTrue(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testDefault_3()
    {
        bindHeaders.put("A", "Value of A");

        matchHeaders.setString("A", "Altered value of A");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertFalse(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testAll_1()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");

        matchHeaders.setString("A", "Value of A");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertTrue(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testAll_2()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.setString("A", "Value of A");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertFalse(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testAll_3()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Value of B");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertTrue(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testAll_4()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Value of B");
        matchHeaders.setString("C", "Value of C");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertTrue(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testAll_5()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Altered value of B");
        matchHeaders.setString("C", "Value of C");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertFalse(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testAny_1()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");

        matchHeaders.setString("A", "Value of A");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertTrue(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testAny_2()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.setString("A", "Value of A");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertTrue(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testAny_3()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Value of B");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertTrue(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testAny_4()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Value of B");
        matchHeaders.setString("C", "Value of C");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertTrue(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testAny_5()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Altered value of B");
        matchHeaders.setString("C", "Value of C");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertTrue(new HeadersBinding(b).matches(matchHeaders));
    }

    public void testAny_6()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.setString("A", "Altered value of A");
        matchHeaders.setString("B", "Altered value of B");
        matchHeaders.setString("C", "Value of C");

        Binding b = new Binding(null, getQueueName(), _queue, null, bindHeaders);
        assertFalse(new HeadersBinding(b).matches(matchHeaders));
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(HeadersBindingTest.class);
    }
}
