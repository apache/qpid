using System;
using System.Collections.Generic;
using System.Collections;
using System.Text;
using Qpid.Messaging;
using Qpid.Client.Qms;
using log4net;
using Qpid.Client.Tests.interop.TestCases;

namespace Qpid.Client.Tests.interop
{
    /// <summary>
    /// Implements a test client as described in the interop testing spec
    /// (http://cwiki.apache.org/confluence/display/qpid/Interop+Testing+Specification). A test client is an agent that
    /// reacts to control message sequences send by the test coordinator.
    ///
    /// <p/><table><caption>Messages Handled by TestClient</caption>
    /// <tr><th> Message               <th> Action
    /// <tr><td> Invite(compulsory)    <td> Reply with Enlist.
    /// <tr><td> Invite(test case)     <td> Reply with Enlist if test case available.
    /// <tr><td> AssignRole(test case) <td> Reply with Accept Role if matches an enlisted test. Keep test parameters.
    /// <tr><td> Start                 <td> Send test messages defined by test parameters. Send report on messages sent.
    /// <tr><td> Status Request        <td> Send report on messages received.
    /// </table>
    ///
    /// <p><table id="crc"><caption>CRC Card</caption>
    /// <tr><th> Responsibilities <th> Collaborations
    /// <tr><td> Handle all incoming control messages. <td> {@link InteropClientTestCase}
    /// <tr><td> Configure and look up test cases by name. <td> {@link InteropClientTestCase}
    /// </table>
    /// </summary>
    class TestClient
    {
        private static ILog log = LogManager.GetLogger(typeof(TestClient));

        /// <summary> Defines the default broker for the tests, localhost, default port. </summary>
        public static string DEFAULT_BROKER_URL = "amqp://guest:guest@clientid/?brokerlist='tcp://localhost:5672'";

        /// <summary> Defines the default virtual host to use for the tests, none. </summary>
        public static string DEFAULT_VIRTUAL_HOST = "";

        /// <summary> Defines the default identifying name of this test client. </summary>
        public static string DEFAULT_CLIENT_NAME = ".net";

        /// <summary> Holds the URL of the broker to run the tests on. </summary>
        public static string brokerUrl;

        /// <summary> Holds the virtual host to run the tests on. If <tt>null</tt>, then the default virtual host is used. </summary>
        public static string virtualHost;

        /// <summary> The clients identifying name to print in test results and to distinguish from other clients. </summary>
        private string clientName;

        /// <summary> Holds all the test cases. </summary>
        private IDictionary testCases = new Hashtable();

        InteropClientTestCase currentTestCase;

        private MessagePublisherBuilder publisherBuilder;

        private IChannel channel;


        /// <summary>
        /// Creates a new interop test client, listenting to the specified broker and virtual host, with the specified
        /// client identifying name.
        /// </summary>
        ///
        /// <param name="brokerUrl"> The url of the broker to connect to. </param>
        /// <param name="virtualHost"> The virtual host to conect to. </param>
        /// <param name="clientName">  The client name to use. </param>
        public TestClient(string brokerUrl, string virtualHost, string clientName)
        {
            log.Debug("public TestClient(string brokerUrl = " + brokerUrl + ", string virtualHost = " + virtualHost
                + ", string clientName = " + clientName + "): called");

            // Retain the connection parameters.
            TestClient.brokerUrl = brokerUrl;
            TestClient.virtualHost = virtualHost;
            this.clientName = clientName;
        }

    
        /// <summary>
        /// The entry point for the interop test coordinator. This client accepts the following command line arguments:
        /// </summary>
        /// 
        /// <p/><table>
        /// <tr><td> -b         <td> The broker URL.       <td> Optional.
        /// <tr><td> -h         <td> The virtual host.     <td> Optional.
        /// <tr><td> -n         <td> The test client name. <td> Optional.
        /// <tr><td> name=value <td> Trailing argument define name/value pairs. Added to system properties. <td> Optional.
        /// </table>
        ///
        /// <param name="args"> The command line arguments. </param>
        public static void Main(string[] args)
        {
            // Extract the command line options (Not exactly Posix but it will do for now...).
            string brokerUrl = DEFAULT_BROKER_URL;
            string virtualHost = DEFAULT_VIRTUAL_HOST;
            string clientName = DEFAULT_CLIENT_NAME;

            foreach (string nextArg in args)
            {
                if (nextArg.StartsWith("-b"))
                {
                    brokerUrl = nextArg.Substring(2);
                }
                else if (nextArg.StartsWith("-h"))
                {
                    virtualHost = nextArg.Substring(2);
                }
                else if (nextArg.StartsWith("-n"))
                {
                    clientName = nextArg.Substring(2);
                }
            }

            // Create a test client and start it running.
            TestClient client = new TestClient(brokerUrl, virtualHost, clientName);

            try
            {
                client.Start();
            }
            catch (Exception e)
            {
                log.Error("The test client was unable to start.", e);
                System.Environment.Exit(1);
            }
        }

        /// <summary>
        /// Starts the interop test client running. This causes it to start listening for incoming test invites.
        /// </summary>
        private void Start()
        {
            log.Debug("private void Start(): called");

            // Use a class path scanner to find all the interop test case implementations.
            ArrayList testCaseClasses = new ArrayList();

            // ClasspathScanner.getMatches(InteropClientTestCase.class, "^TestCase.*", true);
            // Hard code the test classes till the classpath scanner is fixed.
            testCaseClasses.Add(typeof(TestCase1DummyRun));
            testCaseClasses.Add(typeof(TestCase2BasicP2P));
            testCaseClasses.Add(typeof(TestCase3BasicPubSub));

            // Create all the test case implementations and index them by the test names.
            foreach (Type testClass in testCaseClasses)
            {
                InteropClientTestCase testCase = (InteropClientTestCase)Activator.CreateInstance(testClass);
                testCases.Add(testCase.GetName(), testCase);
            }

            // Open a connection to communicate with the coordinator on.
            IConnection connection = CreateConnection(brokerUrl, virtualHost);

            channel = connection.CreateChannel(false, AcknowledgeMode.AutoAcknowledge);

            // Set this up to listen for control messages.
            string responseQueueName = channel.GenerateUniqueName();
            channel.DeclareQueue(responseQueueName, false, true, true);

            channel.Bind(responseQueueName, ExchangeNameDefaults.DIRECT, "iop.control." + clientName);
            channel.Bind(responseQueueName, ExchangeNameDefaults.DIRECT, "iop.control");

            IMessageConsumer consumer = channel.CreateConsumerBuilder(responseQueueName)
                .Create();
            consumer.OnMessage += new MessageReceivedDelegate(OnMessage);

            // Create a publisher to send replies with.
            publisherBuilder = channel.CreatePublisherBuilder()
                .WithExchangeName(ExchangeNameDefaults.DIRECT);
                

            // Start listening for incoming control messages.
            connection.Start();
            Console.WriteLine("Test client " + clientName + " ready to receive test control messages...");
        }

        /// <summary>
        /// Establishes an AMQ connection. This is a simple convenience method for code that does not anticipate handling connection failures. 
        /// All exceptions that indicate that the connection has failed, are allowed to fall through.
        /// </summary>
        ///
        /// <param name="brokerUrl">   The broker url to connect to, <tt>null</tt> to use the default from the properties. </param>
        /// <param name="virtualHost"> The virtual host to connectio to, <tt>null</tt> to use the default. </param>
        ///
        /// <returns> A JMS conneciton. </returns>
        public static IConnection CreateConnection(string brokerUrl, string virtualHost)
        {
            log.Debug("public static Connection createConnection(string brokerUrl = " + brokerUrl + ", string virtualHost = " 
                + virtualHost + "): called");

            // Create a connection to the broker.
            IConnectionInfo connectionInfo = QpidConnectionInfo.FromUrl(brokerUrl);
            connectionInfo.VirtualHost = virtualHost;
            IConnection connection = new AMQConnection(connectionInfo);

            return connection;
        }
        
        /// <summary>
        /// Handles all incoming control messages.
        /// </summary>
        ///
        /// <param name="message"> The incoming message. </param>
        public void OnMessage(IMessage message)
        {
            log.Debug("public void OnMessage(IMessage message = " + message + "): called");

            try
            {
                string controlType = message.Headers.GetString("CONTROL_TYPE");
                string testName = message.Headers.GetString("TEST_NAME");

                // Check if the message is a test invite.
                if ("INVITE" == controlType)
                {
                    string testCaseName = message.Headers.GetString("TEST_NAME");

                    // Flag used to indicate that an enlist should be sent. Only enlist to compulsory invites or invites
                    // for which test cases exist.
                    bool enlist = false;

                    if (testCaseName != null)
                    {
                        log.Debug("Got an invite to test: " + testCaseName);

                        // Check if the requested test case is available.
                        InteropClientTestCase testCase = (InteropClientTestCase)testCases[testCaseName];
    
                        if (testCase != null)
                        {
                            // Make the requested test case the current test case.
                            currentTestCase = testCase;
                            enlist = true;
                        }
                    }
                    else
                    {
                        log.Debug("Got a compulsory invite.");

                        enlist = true;
                    }

                    if (enlist)
                    {
                        // Reply with the client name in an Enlist message.
                        IMessage enlistMessage = channel.CreateMessage();
                        enlistMessage.Headers.SetString("CONTROL_TYPE", "ENLIST");
                        enlistMessage.Headers.SetString("CLIENT_NAME", clientName);
                        enlistMessage.Headers.SetString("CLIENT_PRIVATE_CONTROL_KEY", "iop.control." + clientName);
                        enlistMessage.CorrelationId = message.CorrelationId;

                        Send(enlistMessage, message.ReplyToRoutingKey);
                    }
                }
                else if ("ASSIGN_ROLE" == controlType)
                {
                    // Assign the role to the current test case.
                    string roleName = message.Headers.GetString("ROLE");
    
                    log.Debug("Got a role assignment to role: " + roleName);
    
                    Roles role;

                    if (roleName == "SENDER")
                    {
                        role = Roles.SENDER;
                    }
                    else
                    {
                        role = Roles.RECEIVER;
                    }
    
                    currentTestCase.AssignRole(role, message);

                    // Reply by accepting the role in an Accept Role message.
                    IMessage acceptRoleMessage = channel.CreateMessage();
                    acceptRoleMessage.Headers.SetString("CONTROL_TYPE", "ACCEPT_ROLE");
                    acceptRoleMessage.CorrelationId = message.CorrelationId;

                    Send(acceptRoleMessage, message.ReplyToRoutingKey);
                }
                else if ("START" == controlType || "STATUS_REQUEST" == controlType)
                {
                    if ("START" == controlType)
                    {
                        log.Debug("Got a start notification.");

                        // Start the current test case.
                        currentTestCase.Start();
                    }
                    else
                    {
                        log.Debug("Got a status request.");
                    }

                    // Generate the report from the test case and reply with it as a Report message.
                    IMessage reportMessage = currentTestCase.GetReport(channel);
                    reportMessage.Headers.SetString("CONTROL_TYPE", "REPORT");
                    reportMessage.CorrelationId = message.CorrelationId;

                    Send(reportMessage, message.ReplyToRoutingKey);
                }
                else if ("TERMINATE" == controlType)
                {
                    Console.WriteLine("Received termination instruction from coordinator.");

                    // Is a cleaner shutdown needed?
                    System.Environment.Exit(1);
                }
                else
                {
                    // Log a warning about this but otherwise ignore it.
                    log.Warn("Got an unknown control message, controlType = " + controlType + ", message = " + message);
                }
            }
            catch (QpidException e)
            {
                // Log a warning about this, but otherwise ignore it.
                log.Warn("A QpidException occurred whilst handling a message.");
                log.Debug("Got QpidException whilst handling message: " + message, e);
            }
        }

        /// <summary>
        /// Send the specified message using the specified routing key on the direct exchange.
        /// </summary>
        /// 
        /// <param name="message">    The message to send.</param>
        /// <param name="routingKey"> The routing key to send the message with.</param>
        public void Send(IMessage message, string routingKey)
        {
            IMessagePublisher publisher = publisherBuilder.WithRoutingKey(routingKey).Create();
            publisher.Send(message);
        }
    }
}
