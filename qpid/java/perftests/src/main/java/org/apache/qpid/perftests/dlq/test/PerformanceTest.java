package org.apache.qpid.perftests.dlq.test;

import static org.apache.qpid.perftests.dlq.client.Config.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.qpid.perftests.dlq.client.Check;
import org.apache.qpid.perftests.dlq.client.Client;
import org.apache.qpid.perftests.dlq.client.Create;
import org.apache.qpid.perftests.dlq.client.Receiver;
import org.apache.qpid.perftests.dlq.client.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Run a single performance test, based on specific configuration properties.
 */
public class PerformanceTest
{
    private static final Logger _log = LoggerFactory.getLogger(PerformanceFramework.class);
    
    private static CountDownLatch _latch;
    
    private ExecutorService _executor;
    private Properties _props;
    private int _size = 0;
    private int _threads = 0;
    private int _sent = 0;
    private int _consumed = 0;
    private int _rejected = 0;
    private long _started = 0;
    private long _finished = 0;
    
    private String _session;
    private int _count;
    private int _reject;
    private int _rejectCount;
    private int _maxRedelivery;
    private boolean _messageIds;
    private boolean _listener;

    public PerformanceTest(File propertyFile)
    {
        try
        {
            InputStream input = new FileInputStream(propertyFile);
            _props = new Properties();
            _props.load(input);
        }
        catch (IOException ioe)
        {
            throw new RuntimeException("file error with " + propertyFile.getName());
        }
    }
    
    public PerformanceTest(Properties props)
    {
        _props = props;
    }
    
    public boolean test() throws Exception
    {
        Client create = new Create(_props);
        if (!create.connect())
        {
            _log.error("initial connection failed");
            return false;
        }
        create.call();
        create.shutdown();
        
        _executor = Executors.newCachedThreadPool();   
        _threads = Integer.parseInt(_props.getProperty(THREADS));
        _size = Integer.parseInt(_props.getProperty(SIZE));
        _count = Integer.parseInt(_props.getProperty(COUNT));
        _reject = Integer.parseInt(_props.getProperty(REJECT));
        _rejectCount = Integer.parseInt(_props.getProperty(REJECT_COUNT));
        _maxRedelivery = Integer.parseInt(_props.getProperty(MAX_REDELIVERY));
        _session = _props.getProperty(SESSION);
        _listener = Boolean.parseBoolean(_props.getProperty(LISTENER));
        _messageIds = Boolean.parseBoolean(_props.getProperty(MESSAGE_IDS));
        _latch = new CountDownLatch(1);
        _started = System.nanoTime();
        
        Client sender = new Sender(_props);
        sender.connect();
        Future<Integer> send = _executor.submit(sender);
 
        Receiver.reset();
        List<Future<Integer>> receives = new ArrayList<Future<Integer>>();
        List<Client> receivers = new ArrayList<Client>();
        for (int i = 0; i < _threads; i++)
        {
            Client receiver = new Receiver(_props);
	        receiver.connect();
	        receivers.add(receiver);
	        receives.add(_executor.submit(receiver));
        }
        
        try
        {
            _latch.await();
            _finished = System.nanoTime();
	        _sent = send.get();
	        for (Future<Integer> receive : receives)
	        {
		        _consumed += receive.get();
	        }    

	        Client check = new Check(_props);
	        check.connect();
	        _rejected = check.call();
	        check.shutdown();
        }
        catch (Exception e)
        {
            throw new RuntimeException("error running tests", e);
        }
        finally
        {
            sender.shutdown();
            for (Client receiver : receivers)
            {
	            receiver.shutdown();
            }
            _executor.shutdownNow();
        }
        return true;
    }
    
    public void check(PrintStream out)
    {
        StringBuilder error = new StringBuilder();
        if (_sent != _count)
        {
            error.append("sent ").append(_sent).append(" not ").append(_count).append('\n');
        }
        if (_rejected != Receiver.getRejectedCheck())
        {
            error.append("rejected ").append(_rejected).append(" not ").append(Receiver.getRejectedCheck()).append('\n');
        }
        if (_consumed != Receiver.getConsumedCheck())
        {
            error.append("consumed ").append(_consumed).append(" not ").append(Receiver.getConsumedCheck()).append('\n');
        }
        if (error.length() > 0)
        {
            out.print(error.toString());
        }
        else
        {
	        out.println(toString());
        }
    }
    
    public static String getHeader()
    {
        return "sent,received,consumed,rejected,duration";
    }
    
    public String toString()
    {
        String results = String.format("%d,%d,%d,%d,%f", _sent, Receiver.getTotalReceivedCount(), _consumed, _rejected, getDuration());
        return results;
    }
    
    public double getSent()
    {
        return (double) _sent;
    }
    
    public double getConsumed()
    {
        return (double) _consumed;
    }
    
    public double getTotalReceived()
    {
        return (double) Receiver.getTotalReceivedCount();
    }
    
    public double getDuration()
    {
        return ((double) _finished - (double) _started) / 1000000000.0d;
    }
    
    public double getRejected()
    {
        return (double) _rejected;
    }
    
    public double getThroughputIn()
    {
        return getSent() / getDuration();
    }
    
    public double getThroughputOut()
    {
        return getTotalReceived() / getDuration();
    }
    
    public double getBandwidthIn()
    {
        return (getSent() * (double) _size) / getDuration();
    }
    
    public double getBandwidthOut()
    {
        return (getTotalReceived() * (double) _size) / getDuration();
    }
    
    public double getLatency()
    {
        return getDuration() / getTotalReceived();
    }
    
    public static void countDown()
    {
        _latch.countDown();
    }

    public static void main(String[] argv) throws Exception
    {
        if (argv.length != 1)
        {
            throw new IllegalArgumentException("must pass name of propert file as argument");
        }
        
        File propertyFile = new File(argv[0]);
        if (!propertyFile.exists() || !propertyFile.canRead())
        {
            throw new RuntimeException("property file '" + propertyFile.getName() + "' must exist and be readable");
        }
        PerformanceTest client = new PerformanceTest(propertyFile);
        if (client.test())
        {
	        client.check(System.out);
        }
        else
        {
            System.err.println("test connection failure");
        }
    }
}

