package org.apache.qpid.server.exchange;

import java.util.UUID;

import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class FanoutExchangeType implements ExchangeType<FanoutExchange>
{
    public AMQShortString getName()
    {
        return ExchangeDefaults.FANOUT_EXCHANGE_CLASS;
    }

    public FanoutExchange newInstance(UUID id, VirtualHost host, AMQShortString name,
                                     boolean durable, int ticket, boolean autoDelete)
                                     throws AMQException
    {
        FanoutExchange exch = new FanoutExchange();
        exch.initialise(id, host, name, durable, ticket, autoDelete);
        return exch;
    }

    public AMQShortString getDefaultExchangeName()
    {
        return ExchangeDefaults.FANOUT_EXCHANGE_NAME;
    }
}