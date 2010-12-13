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
import javax.jms.Session;

import org.apache.qpid.perftests.dlq.test.PerformanceTest;

public class Receiver extends Client
{
    private MessageConsumer _consumer;
    private boolean _listener;
    private int _reject;
    private int _rejectCount;
    private Map<Integer, Integer> _rejected = new HashMap<Integer, Integer>();
    private int _receivedCount = 0;
    
    private static volatile boolean _stopped;
    private static CountDownLatch _finished;
    private static AtomicInteger _id; 
    private static AtomicInteger _totalReceivedCount;
    private static AtomicInteger _totalConsumedCount;
    private static AtomicInteger _rejectedCount;
    private static int _consumedCheck;
    private static int _rejectedCheck;
     
    public Receiver(Properties props)
    {
        super(props);
        
        _client = String.format("%04d", _id.incrementAndGet());
    }
    
    public static void reset()
    {
        _id = new AtomicInteger(0);
        _totalReceivedCount = new AtomicInteger(0);
        _totalConsumedCount = new AtomicInteger(0);
        _rejectedCount = new AtomicInteger(0);
        _finished = new CountDownLatch(1);
        _stopped = false;
    }
    

    public synchronized void start() throws Exception
    {
        _listener = Boolean.parseBoolean(_props.getProperty(LISTENER));
        _reject = Integer.parseInt(_props.getProperty(REJECT));
        _rejectCount = Integer.parseInt(_props.getProperty(REJECT_COUNT));

        boolean sessionOk = (_transacted || _clientAck) ||
                ((_sessionType == Session.AUTO_ACKNOWLEDGE || _sessionType == Session.DUPS_OK_ACKNOWLEDGE) && _listener);
        _rejectedCheck = (!sessionOk || _messageIds || _maxRedelivery == 0 || _rejectCount < _maxRedelivery) ? 0 : _count / _reject;
        _consumedCheck = (_count - _rejectedCheck); // + (sessionOk ? ((_count / _reject) * _rejectCount) : 0);
            
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
            if (msg != null)
            {
                processMessage(msg);
            }
        }
    }
    
    public void processMessage(Message msg)
    {
        try
        {
            _totalReceivedCount.incrementAndGet();
	        int number = msg.getIntProperty("number");
	        if (number % 100 == 0)
	        {
	            _log.debug("client " + _client + " got message " + number);
	        }
	        
	        boolean rejectMessage = (number % _reject) == 0;
	        if (rejectMessage)
	        {
	            int rejectCount = 0;
	            if (!_rejected.containsKey(number))
	            {
		            _rejected.put(number, 0);
	            }
                rejectCount = _rejected.get(number) + 1;
	            _rejected.put(number, rejectCount);
	            if (rejectCount <= _rejectCount)
	            {
		            if (rejectCount == _maxRedelivery)
		            {
		                _rejectedCount.incrementAndGet();
		                _log.debug("client " + _client + " rejecting message (" + rejectCount + ") " + msg.getJMSMessageID());
		            }
                    if (rejectCount > _maxRedelivery)
                    {
                        throw new RuntimeException("client " + _client + " received message " + msg.getJMSMessageID() +
                                " " + rejectCount + " times");
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
	            _receivedCount++;
		        _totalConsumedCount.incrementAndGet();
	            if (_transacted)
	            {
		            _session.commit();
		        }
	            else if (_clientAck)
	            {
	                msg.acknowledge();
	            }
	        }

            if (_totalConsumedCount.get() >= _consumedCheck && _rejectedCount.get() >= _rejectedCheck)
            {
                _log.debug("stopping receivers after " + _totalConsumedCount.get() + " received and " + _rejectedCount.get() + " rejected");
                _stopped = true;
                _finished.countDown();
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
        return _receivedCount;
    }
    
    public static int getTotalReceivedCount()
    {
        return _totalReceivedCount.get();
    }
    
    public static int getConsumedCheck()
    {
        return _consumedCheck;
    }
    
    public static int getRejectedCheck()
    {
        return _rejectedCheck;
    }
}