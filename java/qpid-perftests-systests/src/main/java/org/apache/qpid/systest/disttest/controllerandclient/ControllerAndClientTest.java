/*
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
package org.apache.qpid.systest.disttest.controllerandclient;

import static org.apache.qpid.systest.disttest.SystemTestConstants.COMMAND_RESPONSE_TIMEOUT;
import static org.apache.qpid.systest.disttest.SystemTestConstants.REGISTRATION_TIMEOUT;
import static org.apache.qpid.systest.disttest.SystemTestConstants.TEST_RESULT_TIMEOUT;

import java.util.Collection;
import java.util.List;

import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.NamingException;

import org.apache.qpid.systest.disttest.ConfigFileTestHelper;
import org.apache.qpid.disttest.client.Client;
import org.apache.qpid.disttest.client.ClientState;
import org.apache.qpid.disttest.controller.Controller;
import org.apache.qpid.disttest.controller.ResultsForAllTests;
import org.apache.qpid.disttest.controller.TestResult;
import org.apache.qpid.disttest.controller.config.Config;
import org.apache.qpid.disttest.jms.ClientJmsDelegate;
import org.apache.qpid.disttest.jms.ControllerJmsDelegate;
import org.apache.qpid.disttest.message.ConsumerParticipantResult;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.message.ProducerParticipantResult;
import org.apache.qpid.disttest.results.aggregation.ITestResult;
import org.apache.qpid.systest.disttest.DistributedTestSystemTestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControllerAndClientTest extends DistributedTestSystemTestBase
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerAndClientTest.class);
    private static final long CLIENT_BACKGROUND_THREAD_WAIT_TIME = 5000;

    private Controller _controller;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _controller = new Controller(new ControllerJmsDelegate(_context), REGISTRATION_TIMEOUT, COMMAND_RESPONSE_TIMEOUT);
        _controller.setTestResultTimeout(TEST_RESULT_TIMEOUT);
    }

    public void testProducerAndConsumerInSeparateClients() throws Exception
    {
        List<TestResult> resultList = runTestsForTwoClients("producerAndConsumerInSeparateClients.json", 1);

        TestResult testResult1 = resultList.get(0);
        assertEquals("Unexpected test name", "Test 1", testResult1.getName());
        List<ParticipantResult> test1ParticipantResults = testResult1.getParticipantResults();
        assertEquals("Unexpected number of participant results for test 1", 2, test1ParticipantResults.size());
        assertParticipantNames(test1ParticipantResults, "participantConsumer1", "participantProducer1");
        ConsumerParticipantResult result = null;
        for (ParticipantResult participantResult : test1ParticipantResults)
        {
            if (participantResult instanceof ConsumerParticipantResult)
            {
                result = (ConsumerParticipantResult)participantResult;
                break;
            }
        }
        assertNotNull("Consumer results not recived", result);
        Collection<Long> latencies = result.getMessageLatencies();
        assertNotNull("Latency results are not collected", latencies);
        assertEquals("Unexpected latency results", 1, latencies.size());
    }

    public void testProducerClient() throws Exception
    {
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue("producerClient");
        MessageConsumer consumer = session.createConsumer(queue);

        // queue is not declared in configuration
        // controller is not able to clean it
        // cleaning manually
        while(consumer.receive(1000l) != null);

        final Config config = ConfigFileTestHelper.getConfigFromResource(getClass(), "produceClient.json");
        _controller.setConfig(config);
        final Client client1 = new Client(new ClientJmsDelegate(_context));
        final Thread client1Thread = createBackgroundClientThread(client1);
        _controller.awaitClientRegistrations();

        ResultsForAllTests results = _controller.runAllTests();
        _controller.stopAllRegisteredClients();

        assertClientThreadsShutdown(client1Thread);
        assertClientsStopped(ClientState.STOPPED, client1);
        assertFalse("Test should have no errors", results.hasErrors());
        List<ITestResult> allTestResults = results.getTestResults();
        assertEquals("Unexpected number of test results", 1, allTestResults.size());
        ITestResult testResult1 = allTestResults.get(0);
        assertEquals("Unexpected test name", "Test 1", testResult1.getName());
        List<ParticipantResult> test1ParticipantResults = testResult1.getParticipantResults();
        assertEquals("Unexpected number of participant results for test 1", 1, test1ParticipantResults.size());
        assertParticipantNames(test1ParticipantResults, "participantProducer1");

        // check message properties
        for (int i=0; i< 10; i++)
        {
            Message message = consumer.receive(1000l);
            assertNotNull("Message " + i + " is not received", message);
            assertEquals("Unexpected priority", i, message.getJMSPriority());
            assertEquals("Unexpected id", i, message.getIntProperty("id"));
            assertEquals("Unexpected test", "test-value", message.getStringProperty("test"));
        }
    }

    public void testProducerAndThreeConsumersInSeparateClients() throws Exception
    {
        List<TestResult> resultList = runTestsForTwoClients("producerAndThreeConsumersInSeparateClients.json", 1);

        TestResult testResult1 = resultList.get(0);
        List<ParticipantResult> test1ParticipantResults = testResult1.getParticipantResults();
        assertEquals("Unexpected number of participant results for test", 4, test1ParticipantResults.size());

        assertParticipantNames(test1ParticipantResults, "participantConsumer1", "participantConsumer2", "participantConsumer3", "participantProducer1");

        ConsumerParticipantResult consumer1 = (ConsumerParticipantResult) test1ParticipantResults.get(0);
        assertEquals(3, consumer1.getNumberOfMessagesProcessed());
        assertEquals(true, consumer1.isSynchronousConsumer());

        ProducerParticipantResult producer1 = (ProducerParticipantResult) test1ParticipantResults.get(3);
        assertEquals(9, producer1.getNumberOfMessagesProcessed());
        assertEquals(2, producer1.getBatchSize());
        assertEquals(50, producer1.getInterval());
    }

    public void testIteratingFeature() throws Exception
    {
        List<TestResult> resultList = runTestsForTwoClients("iteratingFeature.json", 2);

        assertTestResultMessageSize(resultList.get(0), 0, 100, 10);
        assertTestResultMessageSize(resultList.get(1), 1, 200, 5);

    }

    private void assertTestResultMessageSize(TestResult testResult, int iterationNumber, int expectedMessageSize, int expectedNumberOfMessages)
    {
        List<ParticipantResult> test1ParticipantResults = testResult.getParticipantResults();
        assertEquals("Unexpected number of participant results for test", 2, test1ParticipantResults.size());

        ParticipantResult producer1 = test1ParticipantResults.get(1);

        assertEquals(expectedMessageSize, producer1.getPayloadSize());
        assertEquals(iterationNumber, producer1.getIterationNumber());
    }

    public void testTwoTests() throws Exception
    {
        List<TestResult> resultList = runTestsForTwoClients("testWithTwoTests.json", 2);

        assertEquals("Test 1", resultList.get(0).getName());
        assertEquals("Test 2", resultList.get(1).getName());
    }

    private List<TestResult> runTestsForTwoClients(String jsonConfigFile, int expectedNumberOfTests) throws NamingException, InterruptedException
    {
        final Config config = ConfigFileTestHelper.getConfigFromResource(getClass(), jsonConfigFile);
        _controller.setConfig(config);

        final Client client1 = new Client(new ClientJmsDelegate(_context));
        final Client client2 = new Client(new ClientJmsDelegate(_context));

        final Thread client1Thread = createBackgroundClientThread(client1);
        final Thread client2Thread = createBackgroundClientThread(client2);

        _controller.awaitClientRegistrations();

        ResultsForAllTests results = _controller.runAllTests();
        _controller.stopAllRegisteredClients();

        assertClientThreadsShutdown(client1Thread, client2Thread);
        assertClientsStopped(ClientState.STOPPED, client1, client2);

        assertFalse("Test should have no errors", results.hasErrors());

        List<TestResult> allTestResults = (List)results.getTestResults();
        assertEquals("Unexpected number of test results", expectedNumberOfTests, allTestResults.size());

        return allTestResults;
    }


    private void assertParticipantNames(List<ParticipantResult> participants, String... expectedOrderedParticipantNames)
    {
        assertEquals("Size of list of expected participant names is different from actual", expectedOrderedParticipantNames.length, participants.size());

        for (int i = 0; i < expectedOrderedParticipantNames.length; i++)
        {
            String expectedParticipantName = expectedOrderedParticipantNames[i];
            ParticipantResult participant = participants.get(i);
            assertEquals(expectedParticipantName, participant.getParticipantName());
        }
    }

    private void assertClientsStopped(ClientState expectedState, final Client... clients)
    {
        for (Client client : clients)
        {
            assertEquals(client.getClientName() + " in unexpected state", expectedState, client.getState());
        }
    }

    private void assertClientThreadsShutdown(final Thread... clientThreads)
            throws InterruptedException
    {
        for (Thread clientThread : clientThreads)
        {
            clientThread.join(2000);
            assertFalse(clientThread.getName() + " should have shutdown", clientThread.isAlive());
        }
    }

    private Thread createBackgroundClientThread(final Client client) throws NamingException
    {
        final String clientThreadName = client.getClientName() + "-thread";
        final Thread clientThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    client.start();
                    client.waitUntilStopped(CLIENT_BACKGROUND_THREAD_WAIT_TIME);
                }
                finally
                {
                    LOGGER.debug("Client thread {} finished", clientThreadName);
                }
            }
        }, clientThreadName);
        clientThread.start();
        return clientThread;
    }

}
