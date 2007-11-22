package org.apache.qpid.client.perf;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.client.message.TestMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMSProducer implements Runnable
{
    private static final Logger _logger = LoggerFactory.getLogger(JMSProducer.class);

    private String _id;
    private int _messageSize;
    private Connection _connection;
    private Session _session;
    private MessageProducer _producer;
    private Destination _destination;
    private BytesMessage _payload;
    private boolean _transacted;
    private int _ackMode = Session.AUTO_ACKNOWLEDGE;
    private AtomicBoolean _run = new AtomicBoolean(true);
    private long _currentMsgCount;

    /* Not implementing transactions for first phase */
    public JMSProducer(String id,Connection connection, Destination destination,int messageSize, boolean transacted) throws Exception
    {
        _id = id;
        _connection = connection;
        _destination = destination;
        _messageSize = messageSize;
        _transacted = transacted;
    }

    public void run()
    {
        try
        {
            _session = _connection.createSession(_transacted, _ackMode);
            _payload = TestMessageFactory.newBytesMessage(_session, _messageSize);
            _producer = _session.createProducer(_destination);
            // this should speedup the message producer 
            _producer.setDisableMessageTimestamp(true);
        }
        catch(Exception e)
        {
            _logger.error("Error Setting up JMSProducer:"+ _id, e);
        }

        while (_run.get())
        {
            try
            {
                _payload.setJMSCorrelationID(String.valueOf(_currentMsgCount+1));
                _producer.send(_payload);
                _currentMsgCount ++;
            }
            catch(Exception e)
            {
                _logger.error("Error Sending message from JMSProducer:" + _id, e);
            }
        }
        try
        {
            _session.close();
            _connection.close();
        }
        catch(Exception e)
        {
            _logger.error("Error Closing JMSProducer:"+ _id, e);
        }
    }

    public void stopProducing()
    {
        _run.set(false);
        System.out.println("Producer received notification to stop");
    }

    public String getId()
    {
        return _id;
    }

    /* Not worried about synchronizing as accuracy here is not that important.
     * So if this method is invoked the count maybe off by a few digits.
     * But when the test stops, this will always return the proper count.
     */
    public long getCurrentMessageCount()
    {
        return _currentMsgCount;
    }
}
