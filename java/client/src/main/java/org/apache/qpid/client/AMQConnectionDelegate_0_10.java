package org.apache.qpid.client;

import java.io.IOException;

import javax.jms.JMSException;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMQConnectionDelegate_0_10 implements AMQConnectionDelegate
{

    private static final Logger _logger = LoggerFactory.getLogger(AMQConnectionDelegate_0_10.class);
    private AMQConnection _conn;

    public AMQConnectionDelegate_0_10(AMQConnection conn)
    {
        _conn = conn;
    }

    public Session createSession(boolean transacted, int acknowledgeMode, int prefetchHigh, int prefetchLow) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    public void makeBrokerConnection(BrokerDetails brokerDetail) throws IOException, AMQException
    {
        // TODO Auto-generated method stub

    }

    public void resubscribeSessions() throws JMSException, AMQException, FailoverException
    {
        // TODO Auto-generated method stub

    }

}
