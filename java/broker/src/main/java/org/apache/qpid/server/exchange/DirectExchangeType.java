package org.apache.qpid.server.exchange;

import java.util.UUID;

import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class DirectExchangeType implements ExchangeType<DirectExchange>
{
    public AMQShortString getName()
    {
        return ExchangeDefaults.DIRECT_EXCHANGE_CLASS;
    }

    public DirectExchange newInstance(UUID id, VirtualHost host,
                                        AMQShortString name,
                                        boolean durable,
                                        int ticket,
                                        boolean autoDelete) throws AMQException
    {
        DirectExchange exch = new DirectExchange();
        exch.initialise(id, host,name,durable,ticket,autoDelete);
        return exch;
    }

    public AMQShortString getDefaultExchangeName()
    {
        return ExchangeDefaults.DIRECT_EXCHANGE_NAME;
    }
}