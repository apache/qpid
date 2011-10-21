package org.apache.qpid.client.failover;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

public class AddressBasedFailoverBehaviourTest extends FailoverBehaviourTest
{
    @Override
    protected Destination createDestination(Session session) throws JMSException
    {
        return session.createQueue("ADDR:" +getTestQueueName() + "_" + System.currentTimeMillis() + "; {create: always}");
    }
}
