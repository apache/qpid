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

import org.apache.qpid.AMQException;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.TestApplicationRegistry;

public class HeadersExchangeTest extends AbstractHeadersExchangeTest
{
    protected void setUp() throws Exception
    {
        super.setUp();
        ApplicationRegistry.initialise(new TestApplicationRegistry());
    }

    public void testSimple() throws AMQException
    {
        TestQueue q1 = bindDefault("F0000");
        TestQueue q2 = bindDefault("F0000=Aardvark");
        TestQueue q3 = bindDefault("F0001");
        TestQueue q4 = bindDefault("F0001=Bear");
        TestQueue q5 = bindDefault("F0000", "F0001");
        TestQueue q6 = bindDefault("F0000=Aardvark", "F0001=Bear");
        TestQueue q7 = bindDefault("F0000", "F0001=Bear");
        TestQueue q8 = bindDefault("F0000=Aardvark", "F0001");
        TestQueue q9 = bindDefault("F0000=Apple", "F0001=Banana");
        TestQueue q10 = bindDefault("F0000=Apple", "F0001");

        routeAndTest(new Message("Message1", "F0000"), q1);
        routeAndTest(new Message("Message2", "F0000=Aardvark"), q1, q2);
        routeAndTest(new Message("Message3", "F0000=Aardvark", "F0001"), q1, q2, q3, q5, q8);
        routeAndTest(new Message("Message4", "F0000", "F0001=Bear"), q1, q3, q4, q5, q7);
        routeAndTest(new Message("Message5", "F0000=Aardvark", "F0001=Bear"),
                     q1, q2, q3, q4, q5, q6, q7, q8);
        routeAndTest(new Message("Message6", "F0002"));
    }

    public void testAny() throws AMQException
    {
        TestQueue q1 = bindDefault("F0000", "F0001", "X-match=any");
        TestQueue q2 = bindDefault("F0000=Aardvark", "F0001=Bear", "X-match=any");
        TestQueue q3 = bindDefault("F0000", "F0001=Bear", "X-match=any");
        TestQueue q4 = bindDefault("F0000=Aardvark", "F0001", "X-match=any");
        TestQueue q5 = bindDefault("F0000=Apple", "F0001=Banana", "X-match=any");
        TestQueue q6 = bindDefault("F0000=Apple", "F0001", "X-match=any");

        routeAndTest(new Message("Message1", "F0000"), q1, q3);
        routeAndTest(new Message("Message2", "F0000=Aardvark"), q1, q2, q3, q4);
        routeAndTest(new Message("Message3", "F0000=Aardvark", "F0001"), q1, q2, q3, q4, q6);
        routeAndTest(new Message("Message4", "F0000", "F0001=Bear"), q1, q2, q3, q4, q6);
        routeAndTest(new Message("Message5", "F0000=Aardvark", "F0001=Bear"), q1, q2, q3, q4, q6);
        routeAndTest(new Message("Message6", "F0002"));
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(HeadersExchangeTest.class);
    }
}
