package org.apache.qpid.server.exchange;

import java.util.UUID;

import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class HeadersExchangeType implements ExchangeType<HeadersExchange>
{
    public AMQShortString getName()
    {
        return ExchangeDefaults.HEADERS_EXCHANGE_CLASS;
    }

    public HeadersExchange newInstance(UUID id, VirtualHost host, AMQShortString name, boolean durable, int ticket,
            boolean autoDelete) throws AMQException
    {
        HeadersExchange exch = new HeadersExchange();

        exch.initialise(id, host, name, durable, ticket, autoDelete);
        return exch;
    }

    public AMQShortString getDefaultExchangeName()
    {

        return ExchangeDefaults.HEADERS_EXCHANGE_NAME;
    }
}