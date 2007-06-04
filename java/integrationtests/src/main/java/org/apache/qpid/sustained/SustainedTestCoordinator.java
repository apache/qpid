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
 *
 * 
 */
package org.apache.qpid.sustained;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.interop.coordinator.ListeningCoordinatorTest;
import org.apache.qpid.interop.coordinator.TestClientDetails;
import org.apache.qpid.interop.coordinator.testcases.CoordinatingTestCase3BasicPubSub;
import org.apache.qpid.util.ConversationFactory;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SustainedTestCoordinator extends CoordinatingTestCase3BasicPubSub implements ListeningCoordinatorTest
{
    /** Used for debugging. */
    private static final Logger log = Logger.getLogger(SustainedTestCoordinator.class);
    private List<TestClientDetails> _receivers;
    private static final String SUSTAINED_KEY = "Perf_SustainedPubSub";
    Map<String, Object> _testProperties;

    /**
     * Creates a new coordinating test case with the specified name.
     *
     * @param name The test case name.
     */
    public SustainedTestCoordinator(String name)
    {
        super(name);
        _receivers = new LinkedList();
    }

    /**
     * Adds a receiver to this test.
     *
     * @param receiver The contact details of the sending client in the test.
     */
    public void setReceiver(TestClientDetails receiver)
    {
        _receivers.add(receiver);
    }


    /**
     * Performs the a single test run
     *
     * @throws Exception if there was a problem running the test.
     */
    public void testBasicPubSub() throws Exception
    {
        log.debug("public void testSinglePubSubCycle(): called");

        Map<String, Object> testConfig = new HashMap<String, Object>();
        testConfig.put("TEST_NAME", "Perf_SustainedPubSub");
        testConfig.put("SUSTAINED_KEY", SUSTAINED_KEY);
        //testConfig.put("SUSTAINED_MSG_RATE", 10);
        testConfig.put("SUSTAINED_NUM_RECEIVERS", 2);
        testConfig.put("SUSTAINED_UPDATE_INTERVAL", 25);
        testConfig.put("SUSTAINED_UPDATE_KEY", SUSTAINED_KEY + ".UPDATE");
        testConfig.put("ACKNOWLEDGE_MODE", AMQSession.NO_ACKNOWLEDGE);

        sequenceTest(testConfig);
    }

    /**
     * Holds a test coordinating conversation with the test clients. This is the basic implementation of the inner loop
     * of Use Case 5. It consists of assigning the test roles, begining the test and gathering the test reports from the
     * participants.
     *
     * @param testProperties The test case definition.
     *
     * @return The test results from the senders and receivers.
     *
     * @throws javax.jms.JMSException All underlying JMSExceptions are allowed to fall through.
     */
    protected Message[] sequenceTest(Map<String, Object> testProperties) throws JMSException
    {
        log.debug("protected Message[] sequenceTest(Object... testProperties = " + testProperties + "): called");

        Session session = conversationFactory.getSession();
        Destination senderControlTopic = session.createTopic(sender.privateControlKey);

        ConversationFactory.Conversation senderConversation = conversationFactory.startConversation();

        // Assign the sender role to the sending test client.
        Message assignSender = conversationFactory.getSession().createMessage();
        setPropertiesOnMessage(assignSender, testProperties);
        assignSender.setStringProperty("CONTROL_TYPE", "ASSIGN_ROLE");
        assignSender.setStringProperty("ROLE", "SENDER");

        senderConversation.send(senderControlTopic, assignSender);

        //Assign and wait for the receiver ckuebts to be ready.
        _testProperties = testProperties;

        // Wait for the senders to confirm their roles.
        senderConversation.receive();

        assignReceivers();

        // Start the test.
        Message start = session.createMessage();
        start.setStringProperty("CONTROL_TYPE", "START");

        senderConversation.send(senderControlTopic, start);

        // Wait for the test sender to return its report.
        Message senderReport = senderConversation.receive();

        try
        {
            Thread.sleep(500);
        }
        catch (InterruptedException e)
        {
        }

        // Ask the receiver for its report.
        Message statusRequest = session.createMessage();
        statusRequest.setStringProperty("CONTROL_TYPE", "STATUS_REQUEST");


        return new Message[]{senderReport};
    }

    private void assignReceivers()
    {
        for (TestClientDetails receiver : _receivers)
        {
            registerReceiver(receiver);
        }
    }

    private void registerReceiver(TestClientDetails receiver)
    {
        log.info("registerReceiver called for receiver:" + receiver);
        try
        {
            Session session = conversationFactory.getSession();
            Destination receiverControlTopic = session.createTopic(receiver.privateControlKey);
            ConversationFactory.Conversation receiverConversation = conversationFactory.startConversation();
            // Assign the receiver role the receiving client.
            Message assignReceiver = session.createMessage();
            setPropertiesOnMessage(assignReceiver, _testProperties);
            assignReceiver.setStringProperty("CONTROL_TYPE", "ASSIGN_ROLE");
            assignReceiver.setStringProperty("ROLE", "RECEIVER");

            receiverConversation.send(receiverControlTopic, assignReceiver);

            //Don't wait for receiver to be ready.... we can't this is being done in
            // the dispatcher thread, and most likely the acceptance message we
            // want is sitting in the Dispatcher._queue waiting its turn for being
            // dispatched so if we block here we won't can't get the message.             
            // So assume consumer is ready for action.
            //receiverConversation.receive();
        }
        catch (JMSException e)
        {
            log.warn("Unable to assign receiver:" + receiver + ". Due to:" + e.getMessage());
        }
    }

    public void latejoin(Message message)
    {
        try
        {

            TestClientDetails clientDetails = new TestClientDetails();
            clientDetails.clientName = message.getStringProperty("CLIENT_NAME");
            clientDetails.privateControlKey = message.getStringProperty("CLIENT_PRIVATE_CONTROL_KEY");


            registerReceiver(clientDetails);
        }
        catch (JMSException e)
        {
            //swallow
        }
    }

    /**
     * Should provide a translation from the junit method name of a test to its test case name as defined in the interop
     * testing specification. For example the method "testP2P" might map onto the interop test case name
     * "TC2_BasicP2P".
     *
     * @param methodName The name of the JUnit test method.
     *
     * @return The name of the corresponding interop test case.
     */
    public String getTestCaseNameForTestMethod(String methodName)
    {
        return "Perf_SustainedPubSub";
    }
}
