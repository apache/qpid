package org.apache.qpid.extras.exchanges.example;

import java.util.List;
import java.util.Map;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class TestExchange implements Exchange
{

    public void close() throws AMQException
    {
    }

    public void deregisterQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
    }

    public Map<AMQShortString, List<AMQQueue>> getBindings()
    {
        return null;
    }

    public AMQShortString getName()
    {
        return null;
    }

    public AMQShortString getType()
    {
        return null;
    }

    public boolean hasBindings()
    {
        return false;
    }

    public void initialise(VirtualHost host, AMQShortString name, boolean durable, boolean autoDelete)
            throws AMQException
    {
    }

    public boolean isAutoDelete()
    {
        return false;
    }

    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        return false;
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        return false;
    }

    public boolean isBound(AMQShortString routingKey)
    {
        return false;
    }

    public boolean isBound(AMQQueue queue)
    {
        return false;
    }

    public boolean isDurable()
    {
        return false;
    }

    public void registerQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
    }

    public void route(AMQMessage message) throws AMQException
    {
    }

    public int getTicket()
    {
        return 0;
    }

    public void initialise(VirtualHost arg0, AMQShortString arg1, boolean arg2, int arg3, boolean arg4)
            throws AMQException
    {
    }
}
