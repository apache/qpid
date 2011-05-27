/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.qpid.server.security.acl;

import java.util.Arrays;
import java.util.List;

import javax.jms.Connection;
import javax.jms.Session;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;

/**
 * Tests that ACL version 2/3 files following the specification work correctly.
 * 
 * ACL lines that are identical in meaning apart from differences allowed by the specification, such as whitespace or case
 * of tokens are set up for numbered queues and the queues are then created to show that the meaning is correctly parsed by
 * the plugin.
 * 
 * TODO move this to the access-control plugin unit tests instead
 */
public class ExternalACLFileTest extends AbstractACLTestCase
{
    @Override
    public String getConfig()
    {
        return "config-systests-aclv2.xml";
    }

    @Override
    public List<String> getHostList()
    {
        return Arrays.asList("test");
    }

    private void createQueuePrefixList(String prefix, int count)
    {
        try
        {
            Connection conn = getConnection("test", "client", "guest");
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            conn.start();

            //Create n queues
            for (int n = 0; n < count; n++)
            {
                AMQShortString queueName = new AMQShortString(String.format("%s.%03d", prefix, n));
                ((AMQSession<?, ?>) sess).createQueue(queueName, false, false, false);
            }

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }

    private void createQueueNameList(String...queueNames)
    {
        try
        {
            Connection conn = getConnection("test", "client", "guest");
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            conn.start();

            //Create all queues
            for (String queueName : queueNames)
            {
                ((AMQSession<?, ?>) sess).createQueue(new AMQShortString(queueName), false, false, false);
            }

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }

    public void setUpCreateQueueMixedCase() throws Exception
    {
        writeACLFile(
            "test",
            "acl allow client create queue name=mixed.000",
            "ACL ALLOW client CREATE QUEUE NAME=mixed.001",
            "Acl Allow client Create Queue Name=mixed.002",
            "aCL aLLOW client cREATE qUEUE nAME=mixed.003",
            "aCl AlLoW client cReAtE qUeUe NaMe=mixed.004"
        );
    }

    public void testCreateQueueMixedCase()
    {
        createQueuePrefixList("mixed", 5);
    }

    public void setUpCreateQueueContinuation() throws Exception
    {
        writeACLFile(
            "test",
            "acl allow client create queue name=continuation.000",
            "acl allow client create queue \\",
            "   name=continuation.001",
            "acl allow client \\",
            "   create queue \\",
            "   name=continuation.002",
            "acl allow \\",
            "   client \\",
            "   create queue \\",
            "   name=continuation.003",
            "acl \\",
            "   allow \\",
            "   client \\",
            "   create queue \\",
            "   name=continuation.004"
        );
    }

    public void testCreateQueueContinuation()
    {
        createQueuePrefixList("continuation", 5);
    }

    public void setUpCreateQueueWhitespace() throws Exception
    {
        writeACLFile(
            "test",
            "acl allow client create queue name=whitespace.000",
            "acl\tallow\tclient\tcreate\tqueue\tname=whitespace.001",
            "acl allow client create queue name = whitespace.002",
            "acl\tallow\tclient\tcreate\tqueue\tname\t=\twhitespace.003",
            "acl   allow\t\tclient\t   \tcreate\t\t queue\t \t name  \t =\t \twhitespace.004"
        );
    }

    public void testCreateQueueWhitespace()
    {
        createQueuePrefixList("whitespace", 5);
    }

    public void setUpCreateQueueQuoting() throws Exception
    {
        writeACLFile(
            "test",
            "acl allow client create queue name='quoting.ABC.000'",
            "acl allow client create queue name='quoting.*.000'",
            "acl allow client create queue name='quoting.#.000'",
            "acl allow client create queue name='quoting. .000'",
            "acl allow client create queue name='quoting.!@$%.000'"
        );
    }

    public void testCreateQueueQuoting()
    {
        createQueueNameList( 
            "quoting.ABC.000",
            "quoting.*.000",
            "quoting.#.000",
            "quoting. .000",
            "quoting.!@$%.000"
        );
    }
}



