package org.apache.qpid.ping;

import javax.jms.*;

import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQNoConsumersException;
import org.apache.qpid.client.message.TestMessageFactory;
import org.apache.qpid.jms.Session;

/**
 * This abstract class captures functionality that is common to all ping producers. It provides functionality to
 * manage a session, and a convenience method to commit a transaction on the session. It also provides a framework
 * for running a ping loop, and terminating that loop on exceptions or a shutdown handler.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Manage session.
 * <tr><td> Provide clean shutdown on exception or shutdown hook.
 * <tr><td> Provide useable shutdown hook implementation.
 * </table>
 *
 * @author Rupert Smith
 */
public abstract class AbstractPingProducer implements Runnable, ExceptionListener
{
    private static final Logger _logger = Logger.getLogger(AbstractPingProducer.class);

    /** Holds the current Qpid session to send and receive pings on. */
    protected Session _session;

    /** Used to tell the ping loop when to terminate, it only runs while this is true. */
    protected boolean _publish = true;

    /**
     * Creates an AbstractPingProducer on a session.
     */
    public AbstractPingProducer(Session session)
    {
        _session = session;
    }

    /**
     * Generates a test message of the specified size.
     *
     * @param session     The Qpid session under which to generate the message.
     * @param replyQueue  The reply-to destination for the message.
     * @param messageSize The desired size of the message in bytes.
     * @param currentTime The timestamp to add to the message as a "timestamp" property.
     *
     * @return A freshly generated test message.
     *
     * @throws javax.jms.JMSException All underlying JMSException are allowed to fall through.
     */
    public static ObjectMessage getTestMessage(Session session, Queue replyQueue, int messageSize, long currentTime,
                                               boolean persistent) throws JMSException
    {
        ObjectMessage msg;

        if (messageSize != 0)
        {
            msg = TestMessageFactory.newObjectMessage(session, messageSize);
        }
        else
        {
            msg = session.createObjectMessage();
        }

        // Set the messages persistent delivery flag.
        msg.setJMSDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

        // Timestamp the message.
        msg.setLongProperty("timestamp", currentTime);

        // Ensure that the temporary reply queue is set as the reply to destination for the message.
        if (replyQueue != null)
        {
            msg.setJMSReplyTo(replyQueue);
        }

        return msg;
    }

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

    /**
     * Convenience method to commit the transaction on the session associated with this pinger.
     *
     * @throws javax.jms.JMSException If the commit fails and then the rollback fails.
     */
    protected void commitTx() throws JMSException
    {
        if (_session.getTransacted())
        {
            try
            {
                _session.commit();
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
                    _session.rollback();
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
