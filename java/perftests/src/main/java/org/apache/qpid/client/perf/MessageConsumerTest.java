package org.apache.qpid.client.perf;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.Destination;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageConsumerTest extends Options implements Runnable
{
    private static final Logger _logger = LoggerFactory.getLogger(MessageConsumerTest.class);
    private SimpleDateFormat df = new SimpleDateFormat("h:mm a");

    private Map<Integer, JMSConsumer> _consumers = new ConcurrentHashMap<Integer, JMSConsumer>();
    private int _count;
    String _logFileName;
    private long _gracePeriod = 5 * 60 * 1000;
    long _startTime;
    long _totalMsgCount;

    public void start() throws Exception
    {
        this.parseOptions();
        boolean useSameDest = true;
        _logFileName = _logFilePath + "/MessageConsumerTest_" + System.currentTimeMillis();

        // use each destination with a different consumerucer
        if (_consumerCount == destArray.length)
        {
            useSameDest = false;
        }
        for (; _count < _consumerCount; _count++)
        {
            createAndStartConsumer(useSameDest ? destArray[0] : destArray[_count]);
        }
    }

    private void createAndStartConsumer(String routingKey) throws Exception
    {
        AMQConnection con = ConnectionUtility.getInstance().getConnection();
        con.start();
        Destination dest = Boolean.getBoolean("useQueue")? new AMQQueue(con,routingKey) : new AMQTopic(con,routingKey);
        JMSConsumer consumer;
        if (_synchronous)
        {
            consumer = new JMSSyncConsumer(String.valueOf(_count), con, dest, _transacted, Session.AUTO_ACKNOWLEDGE);
            Thread t = new Thread((JMSSyncConsumer) consumer);
            t.setName("JMSSyncConsumer-" + _count);
            t.start();
        }
        else
        {
            consumer = new JMSAsyncConsumer(String.valueOf(_count), con, dest, _transacted, Session.AUTO_ACKNOWLEDGE);
        }
        _consumers.put(_count, consumer);
    }

    private void startTimerThread()
    {
        _startTime = System.currentTimeMillis();
        if(Boolean.getBoolean("collect_stats"))
        {
            Thread t = new Thread(this);
            t.setName("MessageConsumerTest-TimerThread");
            t.start();
        }
        try
        {
            printSummary();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public void run()
    {
        boolean run = true;
        printHeading();
        runReaper();
        try
        {
            while (run)
            {
                Thread.sleep(_logDuration);
                runReaper();

                if (System.currentTimeMillis() + _gracePeriod - _startTime > _expiry)
                {
                    // time to stop the test.
                    for (Integer id : _consumers.keySet())
                    {
                        JMSConsumer consumer = _consumers.get(id);
                        consumer.stopConsuming();
                    }
                    runReaper();
                    run = false;
                }
            }
        }
        catch (InterruptedException e)
        {
            _logger.error("The timer thread exited", e);
        }
    }

    public void runReaper()
    {
        try
        {
            long totalMsgCountThisInterval = 0;

            for (Integer id : _consumers.keySet())
            {
                JMSConsumer consumer = _consumers.get(id);
                totalMsgCountThisInterval = totalMsgCountThisInterval + consumer.getCurrentMessageCount();

            }
            _totalMsgCount = _totalMsgCount + totalMsgCountThisInterval;

            FileWriter _memoryLog = new FileWriter(_logFileName + ".csv",true);
            StringBuffer buf = new StringBuffer();
            Date d = new Date(System.currentTimeMillis());
            double totaltime = d.getTime() - _startTime;
            buf.append(df.format(d)).append(",");
            buf.append(d.getTime()).append(",");
            buf.append(_totalMsgCount).append(",");
            buf.append(_totalMsgCount*1000 /totaltime).append(",");
            buf.append(totalMsgCountThisInterval).append(",");
            buf.append(totalMsgCountThisInterval*1000/_logDuration).append(",");
            buf.append(Runtime.getRuntime().totalMemory() -Runtime.getRuntime().freeMemory()).append("\n");
            buf.append("\n");
            _memoryLog.write(buf.toString());
            _memoryLog.close();
            System.out.println(buf);
        }
        catch (Exception e)
        {
            _logger.error("Error printing info to the log file", e);
        }
    }

    private void printHeading()
    {
        try
        {
            FileWriter _memoryLog = new FileWriter(_logFileName + ".csv",true);
            String s = "Date/Time,Time (ms),total msg count,total rate (msg/sec),interval count,interval rate (msg/sec),memory";
            _memoryLog.write(s);
            _memoryLog.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void printSummary() throws Exception
    {
        if (Boolean.getBoolean("collect_stats"))
        {
            for (Integer id : _consumers.keySet())
            {
                JMSConsumer consumer = _consumers.get(id);
                _totalMsgCount = _totalMsgCount + consumer.getCurrentMessageCount();

            }
        }

        long current = System.currentTimeMillis();
        double time = current - _startTime;
        double ratio = _totalMsgCount*1000/time;
        FileWriter _summaryLog = new FileWriter(_logFileName + "_Summary",true);

        StringBuffer buf = new StringBuffer("MessageConsumerTest \n Test started at : ");
        buf.append(df.format(new Date(_startTime))).append("\n Test finished at : ");
        Date d = new Date(current);
        buf.append(df.format(d)).append("\n Total Time taken (ms):");
        buf.append(time).append("\n Total messages sent:");
        buf.append(_totalMsgCount).append("\n consumer rate:");
        buf.append(ratio).append("\n");
        _summaryLog.write(buf.toString());
        System.out.println("---------- Test Ended -------------");
        _summaryLog.close();
    }

    public static void main(String[] args)
    {
        try
        {
            MessageConsumerTest test = new MessageConsumerTest();
            test.start();
            test.startTimerThread();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

}
