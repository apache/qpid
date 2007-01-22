package org.apache.qpid.ping;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.*;

import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQNoConsumersException;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.message.TestMessageFactory;
import org.apache.qpid.jms.Session;
import org.apache.qpid.framing.AMQShortString;

/**
 * This abstract class captures functionality that is common to all ping producers. It provides functionality to
 * manage a session, and a convenience method to commit a transaction on the session. It also provides a framework
 * for running a ping loop, and terminating that loop on exceptions or a shutdown handler.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Manage the connection.
 * <tr><td> Provide clean shutdown on exception or shutdown hook.
 * <tr><td> Provide useable shutdown hook implementation.
 * <tr><td> Run a ping loop.
 * </table>
 *
 * @author Rupert Smith
 */
public abstract class AbstractPingProducer implements Runnable, ExceptionListener
{
    private static final Logger _logger = Logger.getLogger(AbstractPingProducer.class);

    /** Used to format time stamping output. */
    protected static final SimpleDateFormat timestampFormatter = new SimpleDateFormat("hh:mm:ss:SS");

    /** Used to tell the ping loop when to terminate, it only runs while this is true. */
    protected boolean _publish = true;

    /** Holds the connection handle to the broker. */
    private Connection _connection;

    /** Holds the producer session, need to create test messages. */
    private Session _producerSession;


    /** holds the no of queues the tests will be using to send messages. By default it will be 1 */
    private int _queueCount;
    private static AtomicInteger _queueSequenceID = new AtomicInteger();
    private List<Queue> _queues = new ArrayList<Queue>();

    /**
     * Convenience method for a short pause.
     *
     * @param sleepTime The time in milliseconds to pause for.
     */
    public static void pause(long sleepTime)
    {
        if (sleepTime > 0)
        {
            try
            {
                Thread.sleep(sleepTime);
            }
            catch (InterruptedException ie)
            { }
        }
    }

    public abstract void pingLoop();

    /**
     * Generates a test message of the specified size.
     *
     * @param replyQueue  The reply-to destination for the message.
     * @param messageSize The desired size of the message in bytes.
     *
     * @return A freshly generated test message.
     *
     * @throws javax.jms.JMSException All underlying JMSException are allowed to fall through.
     */
    public ObjectMessage getTestMessage(Queue replyQueue, int messageSize, boolean persistent) throws JMSException
    {
        ObjectMessage msg;

        if (messageSize != 0)
        {
            msg = TestMessageFactory.newObjectMessage(_producerSession, messageSize);
        }
        else
        {
            msg = _producerSession.createObjectMessage();
        }

        // Set the messages persistent delivery flag.
        msg.setJMSDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

        // Timestamp the message.
        msg.setLongProperty("timestamp", System.currentTimeMillis());

        // Ensure that the temporary reply queue is set as the reply to destination for the message.
        if (replyQueue != null)
        {
            msg.setJMSReplyTo(replyQueue);
        }

        return msg;
    }

    /**
     * Stops the ping loop by clearing the publish flag. The current loop will complete before it notices that this
     * flag has been cleared.
     */
    public void stop()
    {
        _publish = false;
    }

    /**
     * Implements a ping loop that repeatedly pings until the publish flag becomes false.
     */
    public void run()
    {
        // Keep running until the publish flag is cleared.
        while (_publish)
        {
            pingLoop();
        }
    }

    /**
     * Callback method, implementing ExceptionListener. This should be registered to listen for exceptions on the
     * connection, this clears the publish flag which in turn will halt the ping loop.
     *
     * @param e The exception that triggered this callback method.
     */
    public void onException(JMSException e)
    {
        _publish = false;
        _logger.debug("There was a JMSException: " + e.getMessage(), e);
    }

    /**
     * Gets a shutdown hook that will cleanly shut this down when it is running the ping loop. This can be registered
     * with the runtime system as a shutdown hook.
     *
     * @return A shutdown hook for the ping loop.
     */
    public Thread getShutdownHook()
    {
        return new Thread(new Runnable()
            {
                public void run()
                {
                    stop();
                }
            });
    }

    public Connection getConnection()
    {
        return _connection;
    }

    public void setConnection(Connection connection)
    {
        this._connection = connection;
    }

    public Session getProducerSession()
    {
        return _producerSession;
    }

    public void setProducerSession(Session session)
    {
        this._producerSession = session;
    }

    public int getQueueCount()
    {
        return _queueCount;
    }

    public void setQueueCount(int queueCount)
    {
        this._queueCount = queueCount;
    }

    /**
     * Creates queues dynamically and adds to the queues list.  This is when the test is being done with
     * multiple queues.
     * @param queueCount
     */
    protected void createQueues(int queueCount)
    {
        for (int i = 0; i < queueCount; i++)
        {
            AMQShortString name = new AMQShortString("Queue_" + _queueSequenceID.incrementAndGet() + "_" + System.currentTimeMillis());
            AMQQueue queue = new AMQQueue(name, name, false, false, false);
            
            _queues.add(queue);
        }
    }

    protected Queue getQueue(int index)
    {
        return _queues.get(index);
    }

    /**
     * Convenience method to commit the transaction on the session associated with this pinger.
     *
     * @throws javax.jms.JMSException If the commit fails and then the rollback fails.
     */
    protected void commitTx(Session session) throws JMSException
    {
        if (session.getTransacted())
        {
            try
            {
                session.commit();
                _logger.trace("Session Commited.");
            }
            catch (JMSException e)
            {
                _logger.trace("JMSException on commit:" + e.getMessage(), e);

                // Warn that the bounce back client is not available.
                if (e.getLinkedException() instanceof AMQNoConsumersException)
                {
                    _logger.debug("No consumers on queue.");
                }

                try
                {
                    session.rollback();
                    _logger.trace("Message rolled back.");
                }
                catch (JMSException jmse)
                {
                    _logger.trace("JMSE on rollback:" + jmse.getMessage(), jmse);

                    // Both commit and rollback failed. Throw the rollback exception.
                    throw jmse;
                }
            }
        }
    }
}
