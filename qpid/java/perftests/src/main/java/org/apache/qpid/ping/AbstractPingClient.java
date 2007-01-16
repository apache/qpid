package org.apache.qpid.ping;

import javax.jms.JMSException;

import org.apache.log4j.Logger;

import org.apache.qpid.jms.Session;

/**
 * Provides functionality common to all ping clients. Provides the ability to manage a session and a convenience method
 * to commit on the current transaction.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Commit the current transcation.
 * </table>
 *
 * @author Rupert Smith
 */
public abstract class AbstractPingClient
{
    private static final Logger _logger = Logger.getLogger(TestPingClient.class);

    /** Used to keep a handle on the JMS session to send replies using. */
    protected Session _session;

    /**
     * Creates an abstract ping client to manage the specified transcation.
     *
     * @param session The session.
     */
    public AbstractPingClient(Session session)
    {
        _session = session;
    }

    /**
     * Convenience method to commit the transaction on the session associated with this bounce back client.
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

                try
                {
                    _session.rollback();
                    _logger.debug("Message rolled back.");
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
