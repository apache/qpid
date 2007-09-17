package org.apache.qpid.client;

import java.io.IOException;

import javax.jms.JMSException;

import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.Session;
import org.apache.qpidity.client.Client;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.jms.SessionImpl;
import org.apache.qpidity.jms.ExceptionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMQConnectionDelegate_0_10 implements AMQConnectionDelegate
{
    /**
     * This class logger.
     */
    private static final Logger _logger = LoggerFactory.getLogger(AMQConnectionDelegate_0_10.class);

    /**
     * The AMQ Connection.
     */
    private AMQConnection _conn;

    /**
     * The QpidConeection instance that is mapped with thie JMS connection.
     */
    org.apache.qpidity.client.Connection _qpidConnection;

    //--- constructor
    public AMQConnectionDelegate_0_10(AMQConnection conn)
    {
        _conn = conn;
    }

    /**
     * create a Session and start it if required.
     */
    public Session createSession(boolean transacted, int acknowledgeMode, int prefetchHigh, int prefetchLow)
            throws JMSException
    {
        _conn.checkNotClosed();
        int channelId = _conn._idFactory.incrementAndGet();
        AMQSession session;
        try
        {         
            session = new AMQSession_0_10(_qpidConnection, _conn, channelId, transacted, acknowledgeMode, prefetchHigh, prefetchLow);
            _conn.registerSession(channelId, session);
            if (_conn._started)
            {
                session.start();
            }
        }
        catch (Exception e)
        {
            throw new JMSAMQException("cannot create session", e);
        }
        return session;
    }

    /**
     * Make a connection with the broker
     *
     * @param brokerDetail The detail of the broker to connect to.
     * @throws IOException
     * @throws AMQException
     */
    public void makeBrokerConnection(BrokerDetails brokerDetail) throws IOException, AMQException
    {
        _qpidConnection = Client.createConnection();
        try
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("creating connection with broker " + " host: " + brokerDetail
                        .getHost() + " port: " + brokerDetail.getPort() + " virtualhost: " + _conn
                        .getVirtualHost() + "user name: " + _conn.getUsername() + "password: " + _conn.getPassword());
            }
            _qpidConnection.connect(brokerDetail.getHost(), brokerDetail.getPort(), _conn.getVirtualHost(),
                                    _conn.getUsername(), _conn.getPassword());
        }
        catch (QpidException e)
        {
            throw new AMQException(AMQConstant.CHANNEL_ERROR, "cannot connect to broker", e);
        }
    }

    /**
     * Not supported at this level.
     */
    public void resubscribeSessions() throws JMSException, AMQException, FailoverException
    {
        //NOT implemented as railover is handled at a lower level
        throw new FailoverException("failing to reconnect during failover, operation not supported.");
    }

}
