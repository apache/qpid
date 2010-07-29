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

using System;
using System.Text;
using log4net;
using Apache.Qpid.Messaging;

namespace Apache.Qpid.Integration.Tests.interop.TestCases
{
    ///
    /// Implements test case 4, from the interop test specification. This test sets up the TC2_P2PMessageSize test for 50
    /// messages, and a variety of message sizes. It checks that the sender and receivers reports both indicate that all
    /// the test messages were transmitted successfully.
    ///
    /// <p><table id="crc"><caption>CRC Card</caption>
    /// <tr><th> Responsibilities <th> Collaborations
    /// <tr><td> Setup p2p test parameters and compare with test output. <td> {@link FrameworkBaseCase}
    /// </table>
    ///
    public class TestCase5PubSubMessageSize : InteropClientTestCase
    {
        /// Used for debugging. 
        private static ILog log = LogManager.GetLogger(typeof(TestCase5PubSubMessageSize));

        /// <summary> The role to be played by the test. </summary>
        private Roles role;

        /// <summary> Holds the count of test messages received. </summary>
        private int messageCount;

        ///<summary>The size of the message to be sent </summary>
        private int messageSize;

        /// <summary> The number of test messages to send. </summary>
        private int numMessages;

        /// <summary> The number of receiver connection to use. </summary>
        private int numReceivers;

        /// <summary> The routing key to send them to on the default direct exchange. </summary>
        private string sendDestination;

        /// <summary> The connections to send/receive the test messages on. </summary>
        private IConnection[] connection;

        /// <summary> The sessions to send/receive the test messages on. </summary>
        private IChannel[] channel;

        /// <summary> The producer to send the test messages with. </summary>
        IMessagePublisher publisher;

        /// <summary>
        /// Creates a new coordinating test case with the specified name.
        ///</summary> 
        /// <returns>The test case name.</returns>
        /// 
        public String GetName()
        {
            log.Info("public String GetName(): called");
            return "TC5_PubSubMessageSize";
        }

        /// <summary>
        /// Determines whether the test invite that matched this test case is acceptable.
        /// </summary>
        ///
        /// <param name="inviteMessage"> The invitation to accept or reject. </param>
        ///
        /// <returns> <tt>true</tt> to accept the invitation, <tt>false</tt> to reject it. </returns>
        public bool AcceptInvite(IMessage inviteMessage)
        {
            log.Info("public boolean AcceptInvite(IMessage inviteMessage = " + inviteMessage + "): called");
            // All invites are acceptable.
            return true;
        }

        public void Start()
        {
            log.Info("public void start(): called");
            // Assuming numMessages = 1
            Start(1);
        }

        public void Start(int numMessages)
        {
            log.Info("public void start("+numMessages+"): called");

            // Check that the sender role is being performed.
            if (role == Roles.SENDER)
            {
                IMessage testMessage = createMessageOfSize(messageSize);
                

                for (int i = 0; i < numMessages; i++)
                {
                    publisher.Send(testMessage);

                    // Increment the message count.
                    messageCount++;
                }
            }

        }

        private IMessage createMessageOfSize(int size)
        {
            IBytesMessage message = channel[0].CreateBytesMessage();
            string messageStr = "Test Message -- Test Message -- Test Message -- Test Message -- Test Message -- Test Message -- Test Message -- ";
            System.Text.ASCIIEncoding encoding = new System.Text.ASCIIEncoding();
            byte[] messageBytes = encoding.GetBytes(messageStr);

            if (size > 0)
            {
                int div = size / messageBytes.Length;
                int mod = size % messageBytes.Length;

                for (int i = 0; i < div; i++)
                {
                    message.WriteBytes(messageBytes);
                }
                if (mod != 0)
                {
                    message.WriteBytes(messageBytes, 0, mod);
                }
            }
            return message;
        }

        public void AssignRole(Roles role, IMessage assignRoleMessage)
        {
            log.Info("public void assignRole(Roles role = " + role + ", IMessage assignRoleMessage = " + assignRoleMessage
                    + "): called");

            // Reset the message count for a new test.
            messageCount = 0;

            // Take note of the role to be played.
            this.role = role;

            // Extract and retain the test parameters.
            numMessages = assignRoleMessage.Headers.GetInt("PUBSUB_NUM_MESSAGES");
            messageSize = assignRoleMessage.Headers.GetInt("messageSize");
            numReceivers = assignRoleMessage.Headers.GetInt("PUBSUB_NUM_RECEIVERS");

            string sendKey = assignRoleMessage.Headers.GetString("PUBSUB_KEY");
            sendDestination = sendKey;

            log.Info("numMessages = " + numMessages);
            log.Info("messageSize = " + messageSize);
            log.Info("sendKey = " + sendKey);
            log.Info("role = " + role);
            
            switch (role)
            {
                // Check if the sender role is being assigned, and set up a single message producer if so.
                case Roles.SENDER:
                    // Create a new connection to pass the test messages on.
                    connection = new IConnection[1];
                    channel = new IChannel[1];

                    connection[0] =
                        TestClient.CreateConnection(TestClient.brokerUrl, TestClient.virtualHost);
                    channel[0] = connection[0].CreateChannel(false, AcknowledgeMode.AutoAcknowledge);

                    // Extract and retain the test parameters.
                    publisher = channel[0].CreatePublisherBuilder()
                        .WithExchangeName(ExchangeNameDefaults.TOPIC)
                        .WithRoutingKey(sendDestination)
                        .WithMandatory(false)
                        .WithImmediate(false)
                        .Create();
                    break;

                // Otherwise the receiver role is being assigned, so set this up to listen for messages on the required number
                // of receiver connections.
                case Roles.RECEIVER:
                    // Create the required number of receiver connections.
                    connection = new IConnection[numReceivers];
                    channel = new IChannel[numReceivers];
                    
                    for (int i = 0; i < numReceivers; i++)
                    {
                        connection[i] =
                            TestClient.CreateConnection(TestClient.brokerUrl, TestClient.virtualHost);
                        channel[i] = connection[i].CreateChannel(false, AcknowledgeMode.AutoAcknowledge);

                        IMessageConsumer consumer = channel[i].CreateConsumerBuilder(sendDestination).Create();
                        consumer.OnMessage += new MessageReceivedDelegate(OnMessage);
                    }

                    break;
            }

            // Start all the connection dispatcher threads running.
            foreach (IConnection con in connection)
            {
                con.Start();
            }

        }

        public IMessage GetReport(IChannel channel)
        {

            log.Info("public Message GetReport(IChannel channel): called");

            // Close the test connection.
            //connection.Stop();

            // Generate a report message containing the count of the number of messages passed.
            IMessage report = channel.CreateMessage();
            //report.Headers.SetString("CONTROL_TYPE", "REPORT");
            report.Headers.SetInt("MESSAGE_COUNT", messageCount);

            return report;
        }


        /// <summary>
        /// Counts incoming test messages.
        /// </summary>
        ///
        /// <param name="message"> The incoming test message. </param>
        public void OnMessage(IMessage message)
        {
            log.Info("public void onMessage(IMessage message = " + message + "): called");

            // Increment the message count.
            messageCount++;
        }

    }
}
