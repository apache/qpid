package org.apache.qpid.client.perf;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;

import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageConsumerTest extends Options implements MessageListener
{
    private static final Logger _logger = LoggerFactory.getLogger(MessageConsumerTest.class);
    private SimpleDateFormat df = new SimpleDateFormat("h:mm a");

    String _logFileName;
    long _startTime;
    long _intervalStartTime;
    long _totalMsgCount;
    long _intervalCount;

    private AMQConnection _connection;
    private Session _session;

    public void init() throws Exception
    {
       this.parseOptions();
       _logFileName = _logFilePath + "/MessageConsumerTest_" + System.currentTimeMillis();
       _connection = ConnectionUtility.getInstance().getConnection();
       _connection.start();
       Destination dest = Boolean.getBoolean("useQueue")? new AMQQueue(_connection,_destination) : new AMQTopic(_connection,_destination);
       _session = _connection.createSession(_transacted, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer _consumer = _session.createConsumer(dest);
        _consumer.setMessageListener(this);
       _startTime = System.currentTimeMillis();
       if(Boolean.getBoolean("collect_stats"))
       {
           printHeading();
           runReaper();
       }
    }

    public void onMessage(Message message)
    {
        try
        {
            /* long msgId = Integer.parseInt(message.getJMSMessageID());
            if (_verifyOrder && _totalMsgCount+1 != msgId)
            {
                _logger.error("Error : Message received out of order in JMSSyncConsumer:" + _id + " message id was " + msgId + " expected: " + _currentMsgCount+1);
            }*/
            _totalMsgCount ++;
            _intervalCount++;
            if(_intervalCount >= _logFrequency)
            {
                _intervalCount = 0;
                if (Boolean.getBoolean("collect_stats"))
                {
                    runReaper();
                }
                if (System.currentTimeMillis() - _startTime >= _expiry)
                {
                    printSummary();
                    _session.close();
                    _connection.stop();
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public void runReaper()
    {
        try
        {
           FileWriter _memoryLog = new FileWriter(_logFileName + ".csv",true);
            StringBuffer buf = new StringBuffer();
            Date d = new Date(System.currentTimeMillis());
            long currentTime = d.getTime();
            long intervalTime =  currentTime -  _intervalStartTime;
            long totalTime = currentTime - _startTime;
            buf.append(df.format(d)).append(",");
            buf.append(d.getTime()).append(",");
            buf.append(" total Msg Count: ").append(_totalMsgCount).append(",");
            if(totalTime > 0 )
                buf.append(" rate: ").append(_totalMsgCount * 1000 / totalTime);
            buf.append(",");
            buf.append(" interval Count: ").append(_intervalCount).append(",");
            if(intervalTime > 0 )
                buf.append(" interval rate: ").append(_intervalCount * 1000 / intervalTime).append(",");
            buf.append(Runtime.getRuntime().totalMemory() -Runtime.getRuntime().freeMemory()).append("\n");
            buf.append("\n");
            _memoryLog.write(buf.toString());
            _memoryLog.close();
            System.out.println(buf);
            _intervalStartTime = d.getTime();
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
            String s = "Date/Time,Time (ms),total msg count,total rate (msg/sec),memory";
            _memoryLog.write(s);
            _memoryLog.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void printSummary()
    {
        try
        {

            long current = System.currentTimeMillis();
            double time = current - _startTime;
            double ratio = _totalMsgCount*1000/time;
            FileWriter _summaryLog = new FileWriter(_logFileName + "_Summary",true);

            StringBuffer buf = new StringBuffer("MessageProducerTest \n Test started at : ");
            buf.append(df.format(new Date(_startTime))).append("\n Test finished at : ");
            Date d = new Date(current);
            buf.append(df.format(d)).append("\n Total Time taken (ms):");
            buf.append(time).append("\n Total messages sent:");
            buf.append(_totalMsgCount).append("\n producer rate:");
            buf.append(ratio).append("\n");
            _summaryLog.write(buf.toString());
            System.out.println("---------- Test Ended -------------");
            _summaryLog.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        try
        {
            MessageConsumerTest test = new MessageConsumerTest();
            test.init();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

}
