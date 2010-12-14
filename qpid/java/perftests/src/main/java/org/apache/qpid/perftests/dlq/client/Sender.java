package org.apache.qpid.perftests.dlq.client;

import java.util.Properties;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

public class Sender extends Client
{
    public Sender(Properties props)
    {
        super(props);
    }

    private MessageProducer _producer;
    
    public void init()
    {
        super.init();
        
        _sessionType = Session.AUTO_ACKNOWLEDGE;
        _transacted = false;
        _clientAck = false;
    }
    
    public Integer call() throws Exception {
        start();
        
        Message msg = createMessage();
        int sent = sendMessages(msg);
        
        return Integer.valueOf(sent);
    }
    
    public void start() throws Exception
    {
        _producer = _session.createProducer(_queue);
        _producer.setDeliveryMode(_persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
        _producer.setDisableMessageID(_messageIdsDisabled);

        _connection.start();
    }

    public Message createMessage()
    {
        // Setup the message to send
        TextMessage msg;
        try
        {
            byte[] bytes = new byte[_count];
            for (int b = 0; b < bytes.length; b++)
            {
                bytes[b] = (byte) "ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(b % 26);
            }
            String content = new String(bytes);
            //Now create the actual message you want to send
            msg = _session.createTextMessage(content);
            return msg;    
        }
        catch (JMSException e)
        {
            _log.error("Unable to create message", e);
            throw new RuntimeException(e);
        }
    }

    public int sendMessages(Message msg)
    {
        for (int i = 0; i < _count; i++)
        {
            if (i % 100 == 0)
            {
                _log.info("message " + i);
            }
            try
            {
                msg.setIntProperty("number", i);
                _producer.send(msg);
            }
            catch (JMSException e)
            {
                _log.error("jms exception in sender", e);
                return i;
            }
        }
        
        return _count;
    }
}

