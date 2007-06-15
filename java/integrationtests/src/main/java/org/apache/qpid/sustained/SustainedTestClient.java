package org.apache.qpid.sustained;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQNoConsumersException;
import org.apache.qpid.client.AMQNoRouteException;
import org.apache.qpid.interop.testclient.testcases.TestCase3BasicPubSub;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements test case 3, basic pub/sub. Sends/received a specified number of messages to a specified route on the
 * default topic exchange, using the specified number of receiver connections. Produces reports on the actual number of
 * messages sent/received.
 *
 * <p><table id="crc"><caption>CRC Card</caption> <tr><th> Responsibilities <th> Collaborations <tr><td> Supply the name
 * of the test case that this implements. <tr><td> Accept/Reject invites based on test parameters. <tr><td> Adapt to
 * assigned roles. <tr><td> Send required number of test messages using pub/sub. <tr><td> Generate test reports.
 * </table>
 */
public class SustainedTestClient extends TestCase3BasicPubSub implements ExceptionListener
{
    /** Used for debugging. */
    private static final Logger log = Logger.getLogger(SustainedTestClient.class);

    /** The role to be played by the test. */
    private Roles role;

    /** The number of test messages to send. */
//    private int numMessages;

    /** The number of receiver connection to use. */
    private int numReceivers;

    /** The routing key to send them to on the default direct exchange. */
    private Destination sendDestination;

    /** The routing key to send updates to on the default direct exchange. */
    private Destination sendUpdateDestination;


    /** The connections to send/receive the test messages on. */
    private Connection[] connection;

    /** The sessions to send/receive the test messages on. */
    private Session[] session;

    /** The producer to send the test messages with. */
    MessageProducer producer;

    /** Adapter that adjusts the send rate based on the updates from clients. */
    SustainedRateAdapter _rateAdapter;

    /**  */
    int updateInterval;

    private boolean _running = true;

    /**
     * Should provide the name of the test case that this class implements. The exact names are defined in the interop
     * testing spec.
     *
     * @return The name of the test case that this implements.
     */
    public String getName()
    {
        log.debug("public String getName(): called");

        return "Perf_SustainedPubSub";
    }

    /**
     * Assigns the role to be played by this test case. The test parameters are fully specified in the assignment
     * message. When this method return the test case will be ready to execute.
     *
     * @param role              The role to be played; sender or receiver.
     * @param assignRoleMessage The role assingment message, contains the full test parameters.
     *
     * @throws JMSException Any JMSException resulting from reading the message are allowed to fall through.
     */
    public void assignRole(Roles role, Message assignRoleMessage) throws JMSException
    {
        log.debug("public void assignRole(Roles role = " + role + ", Message assignRoleMessage = " + assignRoleMessage
                  + "): called");

        // Take note of the role to be played.
        this.role = role;

        // Extract and retain the test parameters.
        numReceivers = assignRoleMessage.getIntProperty("SUSTAINED_NUM_RECEIVERS");
        updateInterval = assignRoleMessage.getIntProperty("SUSTAINED_UPDATE_INTERVAL");
        String sendKey = assignRoleMessage.getStringProperty("SUSTAINED_KEY");
        String sendUpdateKey = assignRoleMessage.getStringProperty("SUSTAINED_UPDATE_KEY");
        int ackMode = assignRoleMessage.getIntProperty("ACKNOWLEDGE_MODE");

        log.debug("numReceivers = " + numReceivers);
        log.debug("updateInterval = " + updateInterval);
        log.debug("ackMode = " + ackMode);
        log.debug("sendKey = " + sendKey);
        log.debug("sendUpdateKey = " + sendUpdateKey);
        log.debug("role = " + role);

        switch (role)
        {
            // Check if the sender role is being assigned, and set up a single message producer if so.
            case SENDER:
                log.info("*********** Creating SENDER");
                // Create a new connection to pass the test messages on.
                connection = new Connection[1];
                session = new Session[1];

                connection[0] =
                        org.apache.qpid.interop.testclient.TestClient.createConnection(org.apache.qpid.interop.testclient.TestClient.DEFAULT_CONNECTION_PROPS_RESOURCE, org.apache.qpid.interop.testclient.TestClient.brokerUrl,
                                                                                       org.apache.qpid.interop.testclient.TestClient.virtualHost);
                session[0] = connection[0].createSession(false, ackMode);

                // Extract and retain the test parameters.
                sendDestination = session[0].createTopic(sendKey);

                connection[0].setExceptionListener(this);

                producer = session[0].createProducer(sendDestination);

                sendUpdateDestination = session[0].createTopic(sendUpdateKey);
                MessageConsumer updateConsumer = session[0].createConsumer(sendUpdateDestination);

                _rateAdapter = new SustainedRateAdapter(this);
                updateConsumer.setMessageListener(_rateAdapter);


                break;

                // Otherwise the receiver role is being assigned, so set this up to listen for messages on the required number
                // of receiver connections.
            case RECEIVER:
                log.info("*********** Creating RECEIVER");
                // Create the required number of receiver connections.
                connection = new Connection[numReceivers];
                session = new Session[numReceivers];

                for (int i = 0; i < numReceivers; i++)
                {
                    connection[i] =
                            org.apache.qpid.interop.testclient.TestClient.createConnection(org.apache.qpid.interop.testclient.TestClient.DEFAULT_CONNECTION_PROPS_RESOURCE,
                                                                                           org.apache.qpid.interop.testclient.TestClient.brokerUrl,
                                                                                           org.apache.qpid.interop.testclient.TestClient.virtualHost);
                    session[i] = connection[i].createSession(false, ackMode);

                    sendDestination = session[i].createTopic(sendKey);

                    sendUpdateDestination = session[i].createTopic(sendUpdateKey);

                    MessageConsumer consumer = session[i].createConsumer(sendDestination);

                    consumer.setMessageListener(new SustainedListener(TestClient.CLIENT_NAME + "-" + i, updateInterval, session[i], sendUpdateDestination));
                }

                break;
        }

        // Start all the connection dispatcher threads running.
        for (int i = 0; i < connection.length; i++)
        {
            connection[i].start();
        }
    }

    /** Performs the test case actions. */
    public void start() throws JMSException
    {
        log.debug("public void start(): called");

        // Check that the sender role is being performed.
        switch (role)
        {
            // Check if the sender role is being assigned, and set up a single message producer if so.
            case SENDER:
                Message testMessage = session[0].createTextMessage("test");

//            for (int i = 0; i < numMessages; i++)
                while (_running)
                {
                    producer.send(testMessage);

                    _rateAdapter.sentMessage();
                }
                break;
            case RECEIVER:

        }
    }

    /**
     * Gets a report on the actions performed by the test case in its assigned role.
     *
     * @param session The session to create the report message in.
     *
     * @return The report message.
     *
     * @throws JMSException Any JMSExceptions resulting from creating the report are allowed to fall through.
     */
    public Message getReport(Session session) throws JMSException
    {
        log.debug("public Message getReport(Session session): called");

        // Close the test connections.
        for (int i = 0; i < connection.length; i++)
        {
            connection[i].close();
        }

        Message report = session.createMessage();
        report.setStringProperty("CONTROL_TYPE", "REPORT");

        return report;
    }

    public void onException(JMSException jmsException)
    {
        Exception linked = jmsException.getLinkedException();

        if (linked != null)
        {
            if (linked instanceof AMQNoRouteException)
            {
                log.warn("No route .");
            }
            else if (linked instanceof AMQNoConsumersException)
            {
                log.warn("No clients currently available for message:" + ((AMQNoConsumersException) linked).getUndeliveredMessage());
            }
            else
            {

                log.warn("LinkedException:" + linked);
            }

            _rateAdapter.NO_CLIENTS = true;
        }
        else
        {
            log.warn("Exception:" + linked);
        }
    }

    class SustainedListener implements MessageListener
    {
        private int _received = 0;
        private int _updateInterval = 0;
        private Long _time;
        MessageProducer _updater;
        Session _session;
        String _client;


        public SustainedListener(String clientname, int updateInterval, Session session, Destination sendDestination) throws JMSException
        {
            _updateInterval = updateInterval;
            _client = clientname;
            _session = session;
            _updater = session.createProducer(sendDestination);
        }

        public void setReportInterval(int reportInterval)
        {
            _updateInterval = reportInterval;
            _received = 0;
        }

        public void onMessage(Message message)
        {
            if (log.isDebugEnabled())
            {
                log.debug("Message " + _received + "received in listener");
            }

            if (message instanceof TextMessage)
            {

                try
                {
                    if (((TextMessage) message).getText().equals("test"))
                    {
                        if (_received == 0)
                        {
                            _time = System.nanoTime();
                            sendStatus(0, _received);
                        }

                        _received++;

                        if (_received % _updateInterval == 0)
                        {
                            Long currentTime = System.nanoTime();

                            try
                            {
                                sendStatus(currentTime - _time, _received);
                                _time = currentTime;
                            }
                            catch (JMSException e)
                            {
                                log.error("Unable to send update.");
                            }
                        }

                    }
                }
                catch (JMSException e)
                {
                    //ignore error
                }
            }
        }

        private void sendStatus(long time, int received) throws JMSException
        {
            Message updateMessage = _session.createTextMessage("update");
            updateMessage.setStringProperty("CLIENT_ID", _client);
            updateMessage.setStringProperty("CONTROL_TYPE", "UPDATE");
            updateMessage.setLongProperty("RECEIVED", received);
            updateMessage.setLongProperty("DURATION", time);

            log.info("**** SENDING **** CLIENT_ID:" + _client + " RECEIVED:" + received + " DURATION:" + time);

            _updater.send(updateMessage);
        }

    }

    class SustainedRateAdapter implements MessageListener
    {
        private SustainedTestClient _client;
        private long _variance = 250; //no. messages to allow drifting
        private volatile long _delay;   //in nanos
        private long _sent;
        private Map<String, Long> _slowClients = new HashMap<String, Long>();
        private static final long PAUSE_SLEEP = 10; // 10 ms
        private static final long NO_CLIENT_SLEEP = 1000; // 1s 
        private static final long MAX_MESSAGE_DRIFT = 1000; // no messages drifted from producer
        private volatile boolean NO_CLIENTS = true;
        private int _delayShifting;
        private static final int REPORTS_WITHOUT_CHANGE = 10;
        private static final double MAXIMUM_DELAY_SHIFT = .02; //2% 

        SustainedRateAdapter(SustainedTestClient client)
        {
            _client = client;
        }

        public void onMessage(Message message)
        {
            if (log.isDebugEnabled())
            {
                log.debug("SustainedRateAdapter onMessage(Message message = " + message + "): called");
            }

            try
            {
                String controlType = message.getStringProperty("CONTROL_TYPE");

                // Check if the message is a test invite.
                if ("UPDATE".equals(controlType))
                {
                    NO_CLIENTS = false;
                    long duration = message.getLongProperty("DURATION");
                    long received = message.getLongProperty("RECEIVED");
                    String client = message.getStringProperty("CLIENT_ID");

                    log.info("**** SENDING **** CLIENT_ID:" + client + " RECEIVED:" + received + " DURATION:" + duration);


                    recordSlow(client, received);

                    adjustDelay(client, received, duration);
                }
            }
            catch (JMSException e)
            {
                //
            }
        }

        class Pair<X, Y>
        {
            X item1;
            Y item2;

            Pair(X i1, Y i2)
            {
                item1 = i1;
                item2 = i2;
            }

            X getItem1()
            {
                return item1;
            }

            Y getItem2()
            {
                return item2;
            }
        }

        Map<String, Pair<Long, Long>> delays = new HashMap<String, Pair<Long, Long>>();
        Long totalReceived = 0L;
        Long totalDuration = 0L;

        private void adjustDelay(String client, long received, long duration)
        {
            Pair<Long, Long> current = delays.get(client);

            if (current == null)
            {
                delays.put(client, new Pair<Long, Long>(received, duration));
            }
            else
            {
                //reduce totals
                totalReceived -= current.getItem1();
                totalDuration -= current.getItem2();
            }

            totalReceived += received;
            totalDuration += duration;

            long averageDuration = totalDuration / delays.size();

            long diff = Math.abs(_delay - averageDuration);

            //if the averageDuration differs from the current by more than the specified variane then adjust delay.
            if (diff > _variance)
            {
                if (averageDuration > _delay)
                {
                    // we can go faster
                    _delay -= diff;
                    if (_delay < 0)
                    {
                        _delay = 0;
                    }
                }
                else
                {
                    // we need to slow down
                    _delay += diff;
                }
                delayChanged();
            }
            else
            {
                delayStable();
            }

        }

        private void delayChanged()
        {
            _delayShifting = REPORTS_WITHOUT_CHANGE;
        }

        private void delayStable()
        {
            _delayShifting--;

            if (_delayShifting < 0)
            {
                _delayShifting = 0;
                log.info("Delay stabilised:" + _delay);
            }
        }

        // Record Slow clients
        private void recordSlow(String client, long received)
        {
            if (received < (_sent - _variance))
            {
                _slowClients.put(client, received);
            }
            else
            {
                _slowClients.remove(client);
            }
        }

        public void sentMessage()
        {
            if (_sent % updateInterval == 0)
            {

                // Cause test to pause when we have slow
                if (!_slowClients.isEmpty() || NO_CLIENTS)
                {
                    log.info("Pausing for slow clients");

                    //_delay <<= 1;

                    while (!_slowClients.isEmpty())
                    {
                        sleep(PAUSE_SLEEP);
                    }

                    if (NO_CLIENTS)
                    {
                        sleep(NO_CLIENT_SLEEP);
                    }

                    log.debug("Continuing");
                    return;
                }
                else
                {
                    log.info("Delay:" + _delay);
                }
            }

            _sent++;

            if (_delay > 0)
            {
                // less than 10ms sleep doesn't work.
                // _delay is in nano seconds
                if (_delay < 1000000)
                {
                    sleep(0, (int) _delay);
                }
                else
                {
                    if (_delay < 30000000000L)
                    {
                        sleep(_delay / 1000000, (int) (_delay % 1000000));
                    }
                }
            }
        }

        private void sleep(long sleep)
        {
            sleep(sleep, 0);
        }

        private void sleep(long milli, int nano)
        {
            try
            {
                log.debug("Sleep:" + milli + ":" + nano);
                Thread.sleep(milli, nano);
            }
            catch (InterruptedException e)
            {
                //
            }
        }
    }

}
