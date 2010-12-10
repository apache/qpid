package org.apache.qpid.perftests.dlq.client;

import static org.apache.qpid.perftests.dlq.client.Config.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

import org.apache.qpid.perftests.dlq.test.PerformanceTest;



public class Receiver extends Client
{
    private MessageConsumer _consumer;
    private boolean _listener;
    private int _reject;
    private int _rejectCount;
    private Map<Integer, Integer> _rejected = new HashMap<Integer, Integer>();
    
    private static volatile boolean _stopped;
    private static CountDownLatch _finished;
    private static AtomicInteger _id; 
    private static AtomicInteger _received;
     
    public Receiver(Properties props)
    {
        super(props);
        
        _client = String.format("%04d", _id.incrementAndGet());
    }
    
    public static void reset()
    {
        _id = new AtomicInteger(0);
        _received = new AtomicInteger(0);
        _finished = new CountDownLatch(1);
        _stopped = false;
    }

    public void start() throws Exception
    {
        _listener = Boolean.parseBoolean(_props.getProperty(LISTENER));
        _reject = Integer.parseInt(_props.getProperty(REJECT));
        _rejectCount = Integer.parseInt(_props.getProperty(REJECT_COUNT));
        
        _consumer = _session.createConsumer(_queue);
        
        _connection.start();
    }
    
    public void startListener() throws Exception
    {
        _consumer.setMessageListener(new MessageListener()
        {
            public void onMessage(Message msg)
            {
                processMessage(msg);
            }
        });
    }
    
    public void startReceiver() throws Exception
    {
        while (!_stopped)
        {
	        Message msg = _consumer.receive(1000);
            processMessage(msg);
        }
    }
    
    public void processMessage(Message msg)
    {
        try
        {
	        _received.incrementAndGet();
	        int number = msg.getIntProperty("number");
	        if (number % 100 == 0)
	        {
	            _log.info("client " + _client + " got message " + number);
	        }
	        
	        boolean rejectMessage = (number % _reject) == 0;
	        if (rejectMessage)
	        {
	            int rejectCount = 0;
	            if (_rejected.containsKey(number))
	            {
	                rejectCount = _rejected.get(number);
	            }
	            _rejected.put(number, ++rejectCount);
	            if (rejectCount <= _rejectCount)
	            {
		            if (rejectCount >= _maxRedelivery)
		            {
		                _log.info("rejecting message (" + rejectCount + ") " + msg.getJMSMessageID());
		            }
		            if (_transacted)
		            {
		                _session.rollback();
		            }
		            else
		            {
		                _session.recover();
		            }
	            }
	            else
	            {
	                rejectMessage = false;
	            }
	        }
	        
	        if (!rejectMessage)
	        {
	            if (_transacted)
	            {
		            _session.commit();
		        }
	            else if (_clientAck)
	            {
	                msg.acknowledge();
	            }
		        if (number == (_count - 1))
		        {
		            _stopped = true;
		            _finished.countDown();
		        }
	        }
        }
        catch (Exception e)
        {
            _log.error("failed to process message", e);
            _stopped = true;
            _finished.countDown();
        }
    }
    
    public Integer call() throws Exception
    {
        start();
        
        if (_listener)
        {
            startListener();
        }
        else
        {
            startReceiver();
        }
        
        _finished.await();
        PerformanceTest.countDown();
        return _received.get();
    }
}