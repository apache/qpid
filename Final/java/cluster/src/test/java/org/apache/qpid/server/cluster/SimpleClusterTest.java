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
package org.apache.qpid.server.cluster;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;

import javax.jms.JMSException;

import junit.framework.TestCase;

public class SimpleClusterTest extends TestCase
{
    public void testDeclareExchange() throws AMQException, JMSException, URLSyntaxException
    {
        AMQConnection con = new AMQConnection("localhost:9000", "guest", "guest", "test", "/test");
        AMQSession session = (AMQSession) con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        System.out.println("Session created");
        session.declareExchange(new AMQShortString("my_exchange"), new AMQShortString("direct"), true);
        System.out.println("Exchange declared");
        con.close();
        System.out.println("Connection closed");
    }
}
