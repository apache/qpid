package org.apache.qpid.server.exchange;

import java.util.UUID;

import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class TopicExchangeType implements ExchangeType<TopicExchange>
{
    public AMQShortString getName()
    {
        return ExchangeDefaults.TOPIC_EXCHANGE_CLASS;
    }

    public TopicExchange newInstance(UUID id, VirtualHost host,
                                        AMQShortString name,
                                        boolean durable,
                                        int ticket,
                                        boolean autoDelete) throws AMQException
    {
        TopicExchange exch = new TopicExchange();
        exch.initialise(id, host, name, durable, ticket, autoDelete);
        return exch;
    }

    public AMQShortString getDefaultExchangeName()
    {
        return ExchangeDefaults.TOPIC_EXCHANGE_NAME;
    }
}