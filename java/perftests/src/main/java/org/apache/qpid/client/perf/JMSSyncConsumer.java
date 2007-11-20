package org.apache.qpid.client.perf;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMSSyncConsumer implements Runnable, JMSConsumer
{
    private static final Logger _logger = LoggerFactory.getLogger(JMSSyncConsumer.class);

    private String _id;
    private Connection _connection;
    private Session _session;
    private MessageConsumer _consumer;
    private Destination _destination;
    private boolean _transacted;
    private int _ackMode = Session.AUTO_ACKNOWLEDGE;
    private AtomicBoolean _run = new AtomicBoolean(true);
    private long _currentMsgCount;

    /* Not implementing transactions for first phase */
    public JMSSyncConsumer(String id,Connection connection, Destination destination,boolean transacted,int ackMode) throws Exception
    {
        _id = id;
        _connection = connection;
        _destination = destination;
        _transacted = transacted;
        _ackMode = ackMode;
    }

    public void run()
    {
        _run.set(true);

        try
        {
            _session = _connection.createSession(_transacted, _ackMode);
            _consumer = _session.createConsumer(_destination);
        }
        catch(Exception e)
        {
            _logger.error("Error Setting up JMSProducer:"+ _id, e);
        }

        while (_run.get())
        {
            try
            {
                BytesMessage msg = (BytesMessage)_consumer.receive();
                if (msg != null)
                {
                   // long msgId = Integer.parseInt(msg.getJMSCorrelationID());
                    /*if (_currentMsgCount+1 != msgId)
                    {
                        _logger.error("Error : Message received out of order in JMSSyncConsumer:" + _id + " message id was " + msgId + " expected: " + _currentMsgCount+1);
                    }*/
                    _currentMsgCount ++;
                }
            }
            catch(Exception e)
            {
                _logger.error("Error Receiving message from JMSSyncConsumer:" + _id, e);
            }
        }
        try
        {
            _session.close();
            _connection.close();
        }
        catch(Exception e)
        {
            _logger.error("Error Closing JMSSyncConsumer:"+ _id, e);
        }
    }

    public void stopConsuming()
    {
        _run.set(false);
        System.out.println("Consumer received notification to stop");
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
