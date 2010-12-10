package org.apache.qpid.perftests.dlq.client;

import static org.apache.qpid.perftests.dlq.client.Config.*;

import java.util.Properties;

import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.qpid.server.queue.AMQQueueFactory;

public class Check extends Client
{
    private MessageConsumer _consumer;
    private int _reject;
    private int _check;
    
    public Check(Properties props)
    {
        super(props);
    }
    
    public void init()
    {
        super.init();
        
        _queueName = _props.getProperty(QUEUE) + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX;
        _reject = Integer.parseInt(_props.getProperty(REJECT));
        _sessionType = Session.AUTO_ACKNOWLEDGE;
        _transacted = false;
        _clientAck = false;
    }

    public void start() throws Exception
    {
        _consumer = _session.createConsumer(_queue);
        
        _connection.start();
    }
    
    public Integer call() throws Exception {
        start();
        
        Message msg;
        while ((msg = _consumer.receive(1000)) != null)
        {
	        int number = msg.getIntProperty("number");
	        boolean rejectMessage = (number % _reject) == 0;
	        if (!rejectMessage)
	        {
	            throw new RuntimeException("unexpected message on dlq: " + number);
	        }
	        _check++;
        }
        return Integer.valueOf(_check);
    }
}

