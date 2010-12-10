package org.apache.qpid.perftests.dlq.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;

public class Create extends Client
{
    private MessageConsumer _consumer;
    
    public Create(Properties props)
    {
        super(props);
    }
    
    public void init()
    {
        super.init();
        
        _sessionType = Session.AUTO_ACKNOWLEDGE;
        _transacted = false;
        _clientAck = false;
    }

    public void start() throws Exception
    {
        _connection.start();
        
        BindingURL burl = new AMQBindingURL("direct://amq.direct//" + _queueName + "?maxdeliverycount='" + _maxRedelivery + "'");
        _queue = new AMQQueue(burl);

        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put(AMQQueueFactory.X_QPID_DLQ_ENABLED.asString(), true);
        
        ((AMQSession<?,?>) _session).createQueue(new AMQShortString(_queueName), false, false, false, arguments);
        ((AMQSession<?,?>) _session).declareAndBind((AMQDestination) new AMQQueue("amq.direct", _queueName));
        
        _consumer = _session.createConsumer(_queue);
        while (_consumer.receive(1000) != null);
        _consumer.close();
        
        _queue = _session.createQueue(_queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);
        _consumer = _session.createConsumer(_queue);
        while (_consumer.receive(1000) != null);
    }
}

