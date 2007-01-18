package org.apache.qpid.ping;

import java.text.SimpleDateFormat;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQConnection;
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
    /** Used to format time stamping output. */
    protected static final SimpleDateFormat timestampFormatter = new SimpleDateFormat("hh:mm:ss:SS");

    private static final Logger _logger = Logger.getLogger(TestPingClient.class);
    private AMQConnection _connection;

    public AMQConnection getConnection()
    {
        return _connection;
    }

    public void setConnection(AMQConnection _connection)
    {
        this._connection = _connection;
    }

    /**
     * Convenience method to commit the transaction on the session associated with this bounce back client.
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

                try
                {
                    session.rollback();
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
