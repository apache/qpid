package org.apache.qpid.extras.exchanges.example;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeType;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class TestExchangeType implements ExchangeType
{

    public Class getExchangeClass()
    {
        return TestExchange.class;
    }

    public AMQShortString getName()
    {
        return null;
    }

    public Exchange newInstance(VirtualHost host, AMQShortString name, boolean durable, 
                                int token, boolean autoDelete)
            throws AMQException
    {
        TestExchange ex = new TestExchange();
        ex.initialise(host, name, durable, token, autoDelete);
        return ex;
    }

    public AMQShortString getDefaultExchangeName()
    {
        return new AMQShortString("test.exchange");
    }

}
