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

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.protocol.AMQConstant;

/**
 * ACL version 2/3 file testing to verify that ACL entries control queue creation with specific properties.
 * 
 * Tests have their own ACL files that setup specific permissions, and then try to create queues with every possible combination
 * of properties to show that rule matching works correctly. For example, a rule that specified {@code autodelete="true"} for
 * queues with {@link name="temp.true.*"} as well should not affect queues that have names that do not match, or queues that
 * are not autodelete, or both. Also checks that ACL entries only affect the specified users and virtual hosts.
 */
public class ExhaustiveACLTest extends AbstractACLTestCase
{
    @Override
    public String getConfig()
    {
        return "config-systests-aclv2.xml";
    }

    @Override
    public List<String> getHostList()
    {
        return Arrays.asList("test", "test2");
    }
	
    /**
     * Creates a queue.
     * 
     * Connects to the broker as a particular user and create the named queue on a virtual host, with the provided
     * parameters. Uses a new {@link Connection} and {@link Session} and closes them afterwards.
     */
	private void createQueue(String vhost, String user, String name, boolean autoDelete, boolean durable) throws Exception
	{
		Connection conn = getConnection(vhost, user, "guest");	
		Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
		conn.start();
		((AMQSession<?, ?>) sess).createQueue(new AMQShortString(name), autoDelete, durable, false);
		sess.commit();
		conn.close();
	}
	
	/**
	 * Calls {@link #createQueue(String, String, String, boolean, boolean)} with the provided parameters and checks that
	 * no exceptions were thrown.
	 */
	private void createQueueSuccess(String vhost, String user, String name, boolean autoDelete, boolean durable) throws Exception
	{
		try
		{
			createQueue(vhost, user, name, autoDelete, durable);			
		}
		catch (AMQException e)
		{
			fail(String.format("Create queue should have worked for \"%s\" for user %s@%s, autoDelete=%s, durable=%s",
                               name, user, vhost, Boolean.toString(autoDelete), Boolean.toString(durable)));
		}
	}

	/**
	 * Calls {@link #createQueue(String, String, String, boolean, boolean)} with the provided parameters and checks that
	 * the exception thrown was an {@link AMQConstant#ACCESS_REFUSED} or 403 error code. 
	 */
	private void createQueueFailure(String vhost, String user, String name, boolean autoDelete, boolean durable) throws Exception
	{
		try
		{
			createQueue(vhost, user, name, autoDelete, durable);
			fail(String.format("Create queue should have failed for \"%s\" for user %s@%s, autoDelete=%s, durable=%s",
                               name, user, vhost, Boolean.toString(autoDelete), Boolean.toString(durable)));
		}
		catch (AMQException e)
		{
			assertEquals("Should be an ACCESS_REFUSED error", 403, e.getErrorCode().getCode());
		}
	}
	
    public void setUpAuthoriseCreateQueueAutodelete() throws Exception
    {
        writeACLFile("test",
					 "acl allow client access virtualhost",
					 "acl allow server access virtualhost",
					 "acl allow client create queue name=\"temp.true.*\" autodelete=true",
					 "acl allow client create queue name=\"temp.false.*\" autodelete=false",
					 "acl deny client create queue",	
					 "acl allow client delete queue",				 
					 "acl deny all create queue"
            );
    }
    
    /**
     * Test creation of temporary queues, with the autodelete property set to true.
     */
    public void testAuthoriseCreateQueueAutodelete() throws Exception
	{
		createQueueSuccess("test", "client", "temp.true.00", true, false); 
		createQueueSuccess("test", "client", "temp.true.01", true, false);
		createQueueSuccess("test", "client", "temp.true.02", true, true);
		createQueueSuccess("test", "client", "temp.false.03", false, false); 
		createQueueSuccess("test", "client", "temp.false.04", false, false);
		createQueueSuccess("test", "client", "temp.false.05", false, true);
		createQueueFailure("test", "client", "temp.true.06", false, false); 
		createQueueFailure("test", "client", "temp.false.07", true, false);
		createQueueFailure("test", "server", "temp.true.08", true, false); 
		createQueueFailure("test", "client", "temp.other.09", false, false);
		createQueueSuccess("test2", "guest", "temp.true.01", false, false); 
		createQueueSuccess("test2", "guest", "temp.false.02", true, false);
		createQueueSuccess("test2", "guest", "temp.true.03", true, false); 
		createQueueSuccess("test2", "guest", "temp.false.04", false, false);
		createQueueSuccess("test2", "guest", "temp.other.05", false, false);
    }
	
    public void setUpAuthoriseCreateQueue() throws Exception
    {
        writeACLFile("test",
                     "acl allow client access virtualhost",
                     "acl allow server access virtualhost",
                     "acl allow client create queue name=\"create.*\""
            );
    }
    
    /**
     * Tests creation of named queues.
     *
     * If a named queue is specified 
     */
    public void testAuthoriseCreateQueue() throws Exception
    {
        createQueueSuccess("test", "client", "create.00", true, true);
        createQueueSuccess("test", "client", "create.01", true, false);
        createQueueSuccess("test", "client", "create.02", false, true);
        createQueueSuccess("test", "client", "create.03", true, false); 
        createQueueFailure("test", "server", "create.04", true, true);
        createQueueFailure("test", "server", "create.05", true, false);
        createQueueFailure("test", "server", "create.06", false, true);
        createQueueFailure("test", "server", "create.07", true, false); 
        createQueueSuccess("test2", "guest", "create.00", true, true);
        createQueueSuccess("test2", "guest", "create.01", true, false);
        createQueueSuccess("test2", "guest", "create.02", false, true);
        createQueueSuccess("test2", "guest", "create.03", true, false); 
    }
	
    public void setUpAuthoriseCreateQueueBoth() throws Exception
    {
        writeACLFile("test",
                     "acl allow all access virtualhost",
                     "acl allow client create queue name=\"create.*\"",
                     "acl allow all create queue temporary=true"
            );
    }

    /**
     * Tests creation of named queues.
     *
     * If a named queue is specified 
     */
    public void testAuthoriseCreateQueueBoth() throws Exception
    {
        createQueueSuccess("test", "client", "create.00", true, false);
        createQueueSuccess("test", "client", "create.01", false, false);
        createQueueFailure("test", "server", "create.02", false, false);
        createQueueFailure("test", "guest", "create.03", false, false); 
        createQueueSuccess("test", "client", "tmp.00", true, false);
        createQueueSuccess("test", "server", "tmp.01", true, false); 
        createQueueSuccess("test", "guest", "tmp.02", true, false);
        createQueueSuccess("test2", "guest", "create.02", false, false);
    }
}
