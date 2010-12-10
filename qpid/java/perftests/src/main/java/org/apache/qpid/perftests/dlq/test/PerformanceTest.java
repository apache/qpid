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
import org.apache.qpid.perftests.dlq.client.Create;
import org.apache.qpid.perftests.dlq.client.Receiver;
import org.apache.qpid.perftests.dlq.client.Sender;


public class PerformanceTest
{
    private static CountDownLatch _latch;
    
    private ExecutorService _executor;
    private Properties _props;
    private int _size = 0;
    private int _threads = 0;
    private int _sent = 0;
    private int _received = 0;
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
    
    public void test() throws Exception
    {
        Create create = new Create(_props);
        create.connect();
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
        
        Sender sender = new Sender(_props);
        sender.connect();
        Future<Integer> send = _executor.submit(sender);
 
        Receiver.reset();
        List<Future<Integer>> receives = new ArrayList<Future<Integer>>();
        List<Receiver> receivers = new ArrayList<Receiver>();
        for (int i = 0; i < _threads; i++)
        {
	        Receiver receiver = new Receiver(_props);
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
		        _received += receive.get();
	        }    

	        Check check = new Check(_props);
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
            for (Receiver receiver : receivers)
            {
	            receiver.shutdown();
            }
            _executor.shutdownNow();
        }
    }
    
    public void check(PrintStream out)
    {
        StringBuilder error = new StringBuilder();
        if (_sent != _count)
        {
            error.append("sent ").append(_sent).append(" not ").append(_count).append('\n');
        }
        boolean sessionOk = ((_session.equalsIgnoreCase(CLIENT_ACKNOWLEDGE)) || (_session.equalsIgnoreCase(SESSION_TRANSACTED)) ||
                ((_session.equalsIgnoreCase(AUTO_ACKNOWLEDGE) || _session.equalsIgnoreCase(DUPS_OK_ACKNOWLEDGE)) && _listener));
        int rejected = (!sessionOk || !_messageIds || _maxRedelivery == 0 || _rejectCount < _maxRedelivery) ? 0 : _count / _reject;
        if (_rejected != rejected)
        {
            error.append("rejected ").append(_rejected).append(" not ").append(rejected).append('\n');
        }
        int received = (_count - rejected) + (sessionOk ? ((_count / _reject) * _rejectCount) : 0);
        if (_received != received)
        {
            error.append("received ").append(_received).append(" not ").append(received).append('\n');
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
        return "sent,received,rejected,duration";
    }
    
    public String toString()
    {
        String results = String.format("%d,%d,%d,%f", _sent, _received, _rejected, getDuration());
        return results;
    }
    
    public double getSent()
    {
        return (double) _sent;
    }
    
    public double getReceived()
    {
        return (double) _received;
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
        return getReceived() / getDuration();
    }
    
    public double getBandwidthIn()
    {
        return (getSent() * (double) _size) / getDuration();
    }
    
    public double getBandwidthOut()
    {
        return (getReceived() * (double) _size) / getDuration();
    }
    
    public double getLatency()
    {
        return getDuration() / getReceived();
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
        client.test();
        client.check(System.out);
    }
}

