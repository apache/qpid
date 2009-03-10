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
package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidTestCase;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.naming.NamingException;
import java.util.HashMap;
import java.util.Map;

/** The purpose of this set of tests is to ensure */
public class QueueCreateTest extends QpidTestCase
{
    private Connection _connection;
    private AMQSession _session;
    private int _queueCount = 0;

    public void setUp() throws Exception
    {
        _connection = getConnection();

        _session = (AMQSession) _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    }

    private void testQueueWithArguments(Map<String, Object> arguments) throws AMQException
    {
        _session.createQueue(new AMQShortString(this.getName() + (_queueCount++)), false, false, false, arguments);
    }

    public void testCreateNoArguments() throws AMQException, FailoverException
    {
        Map<String, Object> arguments = null;
        testQueueWithArguments(arguments);
    }

    public void testCreatePriorityInt() throws AMQException, FailoverException
    {
        Map<String, Object> arguments = new HashMap<String, Object>();

        //Ensure we can call createQueue with a priority int value
        arguments.put(AMQQueueFactory.X_QPID_PRIORITIES.toString(), 7);
        testQueueWithArguments(arguments);
    }

    /**
     * @link https://issues.apache.org/jira/browse/QPID-1715, QPID-1716
     *
     * @throws AMQException
     * @throws FailoverException
     */
    public void testCreatePriorityString() throws AMQException, FailoverException
    {
        Map<String, Object> arguments = new HashMap<String, Object>();

        //Ensure we can call createQueue with a priority value that is not an int
        arguments.put(AMQQueueFactory.X_QPID_PRIORITIES.toString(), "seven");
        try
        {

            testQueueWithArguments(arguments);
            fail("Invalid Property value still succeeds.");
        }
        catch (Exception e)
        {
            assertTrue("Incorrect error message thrown:" + e.getMessage(),
                       e.getMessage().startsWith("Queue create request with non integer value for :x-qpid-priorities=seven"));
        }
    }

    public void testCreateFlowToDiskValid() throws AMQException, FailoverException
    {
        Map<String, Object> arguments = new HashMap<String, Object>();

        //Ensure we can call createQueue with a priority int value
        arguments.put(AMQQueueFactory.QPID_POLICY_TYPE.toString(), AMQQueueFactory.QPID_FLOW_TO_DISK);
        arguments.put(AMQQueueFactory.QPID_MAX_SIZE.toString(), 100);        
        testQueueWithArguments(arguments);
    }

    /**
     * @link https://issues.apache.org/jira/browse/QPID-1715, QPID-1716
     * @throws AMQException
     * @throws FailoverException
     */
    public void testCreateFlowToDiskValidNoSize() throws AMQException, FailoverException
    {
        Map<String, Object> arguments = new HashMap<String, Object>();

        //Ensure we can call createQueue with a priority int value
        arguments.put(AMQQueueFactory.QPID_POLICY_TYPE.toString(), AMQQueueFactory.QPID_FLOW_TO_DISK);
        try
        {
            testQueueWithArguments(arguments);
        }
        catch (AMQException e)
        {
            assertTrue("Incorrect Error throw:" + e.getMessage() +
                       ":expecting:Queue create request with no qpid.max_size value",
                       e.getMessage().contains("Queue create request with no qpid.max_size value"));
        }
    }

    /**
     * @link https://issues.apache.org/jira/browse/QPID-1715, QPID-1716
     * @throws AMQException
     * @throws FailoverException
     */
    public void testCreateFlowToDiskInvalid() throws AMQException, FailoverException
    {
        Map<String, Object> arguments = new HashMap<String, Object>();

        arguments.put(AMQQueueFactory.QPID_POLICY_TYPE.toString(), "infinite");
        try
        {
            testQueueWithArguments(arguments);
            fail("Invalid Property value still succeeds.");
        }
        catch (Exception e)
        {
            //Check error is correct
            assertTrue("Incorrect error message thrown:" + e.getMessage(),
                       e.getMessage().startsWith("Queue create request with unknown Policy Type:infinite"));
        }

    }

    /**
     * @link https://issues.apache.org/jira/browse/QPID-1715, QPID-1716
     * @throws AMQException
     * @throws FailoverException
     */
    public void testCreateFlowToDiskInvalidSize() throws AMQException, FailoverException
    {
        Map<String, Object> arguments = new HashMap<String, Object>();

        arguments.put(AMQQueueFactory.QPID_POLICY_TYPE.toString(), AMQQueueFactory.QPID_FLOW_TO_DISK);
        arguments.put(AMQQueueFactory.QPID_MAX_SIZE.toString(), -1);
        try
        {
            testQueueWithArguments(arguments);
            fail("Invalid Property value still succeeds.");
        }
        catch (Exception e)
        {
            //Check error is correct
            assertTrue("Incorrect error message thrown:" + e.getMessage(),
                       e.getMessage().startsWith("Queue create request with negative size:-1"));
        }

    }


}
