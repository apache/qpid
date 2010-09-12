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
package org.apache.qpid.server.logging;

import java.io.File;
import java.util.List;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.protocol.AMQConstant;

/**
 * ACL version 2/3 file testing to verify that ACL actor logging works correctly.
 * 
 * This suite of tests validate that the AccessControl messages occur correctly
 * and according to the following format:
 * 
 * <pre>
 * ACL-1001 : Allowed Operation Object {PROPERTIES}
 * ACL-1002 : Denied Operation Object {PROPERTIES}
 * </pre>
 */
public class AccessControlLoggingTest extends AbstractTestLogging
{
    private static final String ACL_LOG_PREFIX = "ACL-";
    private static final String USER = "client";
    private static final String PASS = "guest";

    public void setUp() throws Exception
    {
        setConfigurationProperty("virtualhosts.virtualhost.test.security.aclv2",
                QpidHome + File.separator + "etc" + File.separator + "test-logging.txt");
        
        super.setUp();
    }

    /** FIXME This comes from SimpleACLTest and makes me suspicious. */
    @Override
    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        catch (JMSException e)
        {
            //we're throwing this away as it can happen in this test as the state manager remembers exceptions
            //that we provoked with authentication failures, where the test passes - we can ignore on con close
        }
    }
    
    /**
     * Test that {@code allow} ACL entries do not log anything.
     */
    public void testAllow() throws Exception
	{
        Connection conn = getConnection(USER, PASS);
        Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        conn.start();
        ((AMQSession<?, ?>) sess).createQueue(new AMQShortString("allow"), false, false, false);
        
        List<String> matches = findMatches(ACL_LOG_PREFIX);
        
        assertTrue("Should be no ACL log messages", matches.isEmpty());
    }
    
    /**
     * Test that {@code allow-log} ACL entries log correctly.
     */
    public void testAllowLog() throws Exception
    {
        Connection conn = getConnection(USER, PASS);
        Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        conn.start();
        ((AMQSession<?, ?>) sess).createQueue(new AMQShortString("allow-log"), false, false, false);
        
        List<String> matches = findMatches(ACL_LOG_PREFIX);
        
        assertEquals("Should only be one ACL log message", 1, matches.size());
        
        String log = getLogMessage(matches, 0);
        String actor = fromActor(log);
        String subject = fromSubject(log);
        String message = getMessageString(fromMessage(log));
        
        validateMessageID(ACL_LOG_PREFIX + 1001, log);
        
        assertTrue("Actor should contain the user identity", actor.contains(USER));
        assertTrue("Subject should be empty", subject.length() == 0);
        assertTrue("Message should start with 'Allowed'", message.startsWith("Allowed"));
        assertTrue("Message should contain 'Create Queue'", message.contains("Create Queue"));
        assertTrue("Message should have contained the queue name", message.contains("allow-log"));
    }
    
    /**
     * Test that {@code deny-log} ACL entries log correctly.
     */
    public void testDenyLog() throws Exception
    {
        Connection conn = getConnection(USER, PASS);
        Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        conn.start();
        try {
            ((AMQSession<?, ?>) sess).createQueue(new AMQShortString("deny-log"), false, false, false);
	        fail("Should have denied queue creation");
        }
        catch (AMQException amqe)
        {
            // Denied, so exception thrown
            assertEquals("Expected ACCESS_REFUSED error code", AMQConstant.ACCESS_REFUSED, amqe.getErrorCode());
        }
        
        List<String> matches = findMatches(ACL_LOG_PREFIX);
        
        assertEquals("Should only be one ACL log message", 1, matches.size());
        
        String log = getLogMessage(matches, 0);
        String actor = fromActor(log);
        String subject = fromSubject(log);
        String message = getMessageString(fromMessage(log));
        
        validateMessageID(ACL_LOG_PREFIX + 1002, log);
        
        assertTrue("Actor should contain the user identity", actor.contains(USER));
        assertTrue("Subject should be empty", subject.length() == 0);
        assertTrue("Message should start with 'Denied'", message.startsWith("Denied"));
        assertTrue("Message should contain 'Create Queue'", message.contains("Create Queue"));
        assertTrue("Message should have contained the queue name", message.contains("deny-log"));
    }
    
    /**
     * Test that {@code deny} ACL entries do not log anything.
     */
    public void testDeny() throws Exception
    {
        Connection conn = getConnection(USER, PASS);
        Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        conn.start();
        try {
            ((AMQSession<?, ?>) sess).createQueue(new AMQShortString("deny"), false, false, false);
            fail("Should have denied queue creation");
        }
        catch (AMQException amqe)
        {
            // Denied, so exception thrown
            assertEquals("Expected ACCESS_REFUSED error code", AMQConstant.ACCESS_REFUSED, amqe.getErrorCode());
        }
        
        List<String> matches = findMatches(ACL_LOG_PREFIX);
        
        assertTrue("Should be no ACL log messages", matches.isEmpty());
    }
}
