package org.apache.qpid.client.perf;

import java.io.FileWriter;
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
    long totalMsgCount;
    long _totalMsgCount;
    double _timeElapsed = 0;

    public void start() throws Exception
    {
        this.parseOptions();
        boolean useSameDest = true;
        _logFileName = _logFilePath + "/MessageConsumerTest_" + System.currentTimeMillis();

        // use each destination with a different producer
        if (_producerCount == destArray.length)
        {
            useSameDest = false;
        }
        for (; _count < _producerCount; _count++)
        {
            createAndStartConsumer(useSameDest ? destArray[0] : destArray[_count]);
        }
    }

    private void createAndStartConsumer(String routingKey) throws Exception
    {
        AMQConnection con = ConnectionUtility.getInstance().getConnection();
        con.start();
        Destination dest = Boolean.getBoolean("useQueue")? new AMQQueue(con,routingKey) : new AMQTopic(con,routingKey);
        JMSConsumer prod;
        if (_synchronous)
        {
            prod = new JMSSyncConsumer(String.valueOf(_count), con, dest, _transacted, Session.AUTO_ACKNOWLEDGE);
            Thread t = new Thread((JMSSyncConsumer) prod);
            t.setName("JMSSyncConsumer-" + _count);
            t.start();
        }
        else
        {
            prod = new JMSAsyncConsumer(String.valueOf(_count), con, dest, _transacted, Session.AUTO_ACKNOWLEDGE);
        }
        _consumers.put(_count, prod);
    }

    private void startTimerThread()
    {
        Thread t = new Thread(this);
        t.setName("MessageConsumerTest-TimerThread");
        t.start();
    }

    public void run()
    {
        boolean run = true;
        _startTime = System.currentTimeMillis();
        runReaper(false);
        try
        {
            while (run)
            {
                Thread.sleep(_logDuration);
                runReaper(false);

                if (System.currentTimeMillis() + _gracePeriod - _startTime > _expiry)
                {
                    // time to stop the test.
                    for (Integer id : _consumers.keySet())
                    {
                        JMSConsumer consumer = _consumers.get(id);
                        consumer.stopConsuming();
                    }
                    runReaper(true);
                    run = false;
                }
            }
        }
        catch (InterruptedException e)
        {
            _logger.error("The timer thread exited", e);
        }
    }

    public void runReaper(boolean printSummary)
    {
        try
        {
            FileWriter _logFile = new FileWriter(_logFileName + ".csv", true);
            long newTotalMsgCount = 0;
            long totalMsgCountThisInterval = 0;

            for (Integer id : _consumers.keySet())
            {
                JMSConsumer prod = _consumers.get(id);
                StringBuffer buf = new StringBuffer("JMSSyncConsumer(").append(prod.getId()).append("),");
                Date d = new Date(System.currentTimeMillis());
                buf.append(df.format(d)).append(",");
                buf.append(d.getTime()).append(",");
                buf.append(prod.getCurrentMessageCount()).append("\n");
                _logFile.write(buf.toString());
                newTotalMsgCount = newTotalMsgCount + prod.getCurrentMessageCount();
               totalMsgCountThisInterval = newTotalMsgCount - _totalMsgCount;
               _totalMsgCount = newTotalMsgCount;
            }
            _logFile.close();

            FileWriter _memoryLog = new FileWriter(_logFileName + "_memory.csv",true);
            StringBuffer buf = new StringBuffer("JMSProducer,");
            Date d = new Date(System.currentTimeMillis());
            double totaltime = d.getTime() - _startTime;
            _timeElapsed = totaltime - _timeElapsed;
            buf.append(df.format(d)).append(",");
            buf.append(d.getTime()).append(",");
            buf.append(_totalMsgCount).append(",");
            buf.append(Runtime.getRuntime().totalMemory() -Runtime.getRuntime().freeMemory()).append("\n");
            buf.append("\n");
            buf.append("Throughput: total " + (_totalMsgCount /totaltime)*1000 + " msg/s;  this interval: "  +  (totalMsgCountThisInterval/_timeElapsed)*1000 + " msg/s");
            _memoryLog.write(buf.toString());
            _memoryLog.close();
            System.out.println(buf);
            if (printSummary)
            {
                double dCount = _totalMsgCount;
                double ratio = (dCount/totaltime)*1000;
                FileWriter _summaryLog = new FileWriter(_logFileName + "_Summary",true);
                buf = new StringBuffer("MessageProducerTest \n Test started at : ");
                buf.append(df.format(new Date(_startTime))).append("\n Test finished at : ");
                d = new Date(System.currentTimeMillis());
                buf.append(df.format(d)).append("\n Total Time taken (ms):");
                buf.append(totaltime).append("\n Total messages sent:");
                buf.append(_totalMsgCount).append("\n Producer rate:");
                buf.append(ratio).append("\n");
                _summaryLog.write(buf.toString());
                System.out.println("---------- Test Ended -------------");
                _summaryLog.close();
            }
            _timeElapsed = totaltime;
        }
        catch (Exception e)
        {
            _logger.error("Error printing info to the log file", e);
        }
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
