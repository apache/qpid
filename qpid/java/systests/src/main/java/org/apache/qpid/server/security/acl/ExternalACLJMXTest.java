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

import org.apache.qpid.AMQConnectionClosedException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.test.utils.JMXTestUtils;

/**
 * Tests that ACL entries that apply to AMQP objects also apply when those objects are accessed via JMX.
 */
public class ExternalACLJMXTest extends AbstractACLTestCase
{
    private JMXTestUtils _jmx;
    
    private static final String QUEUE_NAME = "kipper";
    private static final String EXCHANGE_NAME = "amq.kipper";
    
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

    @Override
    public void setUp() throws Exception
    {
        _jmx = new JMXTestUtils(this, "admin", "admin");
        _jmx.setUp();
        super.setUp();
        _jmx.open();
    }
    
    @Override
    public void tearDown() throws Exception
    {
        _jmx.close();
        super.tearDown();
    }

    // test-externalacljmx.txt
    // create queue owner=client # success
    public void testCreateClientQueueSuccess() throws Exception
    {   
        //Queue Parameters
        String queueOwner = "client";
        
        _jmx.createQueue("test", QUEUE_NAME, queueOwner, true);
    }

    // test-externalacljmx.txt
    // create queue owner=client # failure
    public void testCreateServerQueueFailure() throws Exception
    {   
        //Queue Parameters
        String queueOwner = "server";
        
        try
        {
            _jmx.createQueue("test", QUEUE_NAME, queueOwner, true);
            
            fail("Queue create should fail");
        }
        catch (Exception e)
        {
            assertNotNull("Cause is not set", e.getCause());
            assertEquals("Cause message incorrect",
                    "org.apache.qpid.AMQSecurityException: Permission denied: queue-name 'kipper' [error code 403: access refused]", e.getCause().getMessage());
        }
    }

    // no create queue acl in file # failure
    public void testCreateQueueFailure() throws Exception
    {   
        //Queue Parameters
        String queueOwner = "guest";
        
        try
        {
            _jmx.createQueue("test", QUEUE_NAME, queueOwner, true);
            
            fail("Queue create should fail");
        }
        catch (Exception e)
        {
            assertNotNull("Cause is not set", e.getCause());
            assertEquals("Cause message incorrect",
                    "org.apache.qpid.AMQSecurityException: Permission denied: queue-name 'kipper' [error code 403: access refused]", e.getCause().getMessage());
        }
    }

    // test-externalacljmx.txt
    // allow create exchange name=amq.kipper.success
    public void testCreateExchangeSuccess() throws Exception
    {   
        _jmx.createExchange("test", EXCHANGE_NAME + ".success", "direct", true);
    }

    // test-externalacljmx.txt
    // deny create exchange name=amq.kipper.failure
    public void testCreateExchangeFailure() throws Exception
    {   
        try
        {
            _jmx.createExchange("test", EXCHANGE_NAME + ".failure", "direct", true);
            
            fail("Exchange create should fail");
        }
        catch (Exception e)
        {
            assertNotNull("Cause is not set", e.getCause());
            assertEquals("Cause message incorrect",
                    "org.apache.qpid.AMQSecurityException: Permission denied: exchange-name 'amq.kipper.failure' [error code 403: access refused]", e.getCause().getMessage());
        }
    }

    // test-externalacljmx.txt
    // allow create exchange name=amq.kipper.success
    // allow delete exchange name=amq.kipper.success
    public void testDeleteExchangeSuccess() throws Exception
    {   
        _jmx.createExchange("test", EXCHANGE_NAME + ".success", "direct", true);
        _jmx.unregisterExchange("test", EXCHANGE_NAME + ".success");
    }

    // test-externalacljmx-deleteexchangefailure.txt
    // allow create exchange name=amq.kipper.delete
    // deny delete exchange name=amq.kipper.delete
    public void testDeleteExchangeFailure() throws Exception
    {   
        _jmx.createExchange("test", EXCHANGE_NAME + ".delete", "direct", true);
        try
        {
            _jmx.unregisterExchange("test", EXCHANGE_NAME + ".delete");
            
            fail("Exchange delete should fail");
        }
        catch (Exception e)
        {
            assertNotNull("Cause is not set", e.getCause());
            assertEquals("Cause message incorrect",
                    "org.apache.qpid.AMQSecurityException: Permission denied [error code 403: access refused]", e.getCause().getMessage());
        }
    }
    
    /**
     * admin user has JMX right but not AMQP
     */
    public void setUpCreateQueueJMXRights() throws Exception
    {
        writeACLFile("test",
                "ACL ALLOW admin EXECUTE METHOD component=\"VirtualHost.VirtualHostManager\" name=\"createNewQueue\"",
			    "ACL DENY admin CREATE QUEUE");
    }
    
    public void testCreateQueueJMXRights() throws Exception
    {
        try
        {
            _jmx.createQueue("test", QUEUE_NAME, "admin", true);
            
            fail("Queue create should fail");
        }
        catch (Exception e)
        {
            assertNotNull("Cause is not set", e.getCause());
            assertEquals("Cause message incorrect",
                    "org.apache.qpid.AMQSecurityException: Permission denied: queue-name 'kipper' [error code 403: access refused]", e.getCause().getMessage());
        }
    }

    /**
     * admin user has AMQP right but not JMX
     */
    public void setUpCreateQueueAMQPRights() throws Exception
    {
        writeACLFile("test",
	    		"ACL DENY admin EXECUTE METHOD component=\"VirtualHost.VirtualHostManager\" name=\"createNewQueue\"",
	    		"ACL ALLOW admin CREATE QUEUE");
    }
    
    public void testCreateQueueAMQPRights() throws Exception
    {
        try
        {
            _jmx.createQueue("test", QUEUE_NAME, "admin", true);
            
            fail("Queue create should fail");
        }
        catch (Exception e)
        {
            assertEquals("Cause message incorrect", "Permission denied: Execute createNewQueue", e.getMessage());
        }
    }

    /**
     * admin has both JMX and AMQP rights
     */
    public void setUpCreateQueueJMXAMQPRights() throws Exception
    {
        writeACLFile("test",
                    "ACL ALLOW admin EXECUTE METHOD component=\"VirtualHost.VirtualHostManager\" name=\"createNewQueue\"",
                    "ACL ALLOW admin CREATE QUEUE");
    }
    
    public void testCreateQueueJMXAMQPRights() throws Exception
    {
        try
        {
            _jmx.createQueue("test", QUEUE_NAME, "admin", true);
        }
        catch (Exception e)
        {
            fail("Queue create should succeed: " + e.getCause().getMessage());
        }
    }
}
