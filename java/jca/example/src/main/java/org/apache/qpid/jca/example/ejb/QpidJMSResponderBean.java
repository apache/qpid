package org.apache.qpid.jca.example.ejb;

import java.util.Date;

import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    @ActivationConfigProperty(propertyName = "destination", propertyValue = "@qpid.responder.queue.jndi.name@"),
    @ActivationConfigProperty(propertyName = "connectionURL", propertyValue = "@broker.url@"),
    @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "10")
})
public class QpidJMSResponderBean implements MessageListener
{

    private static final Logger _log = LoggerFactory.getLogger(QpidJMSResponderBean.class);

    private ConnectionFactory _connectionFactory;

    @PostConstruct
    public void init()
    {
        InitialContext context = null;

        try
        {
            context = new InitialContext();
            _connectionFactory = (ConnectionFactory)context.lookup("java:comp/env/QpidJMSXA");

        }
        catch(Exception e)
        {
           _log.error(e.getMessage(), e);
        }
        finally
        {
            QpidUtil.closeResources(context);
        }

    }

    @Override
    public void onMessage(Message message)
    {
        Connection connection = null;
        Session session = null;
        MessageProducer messageProducer = null;
        TextMessage response = null;

        try
        {
            if(message instanceof TextMessage)
            {
                String content = ((TextMessage)message).getText();

                _log.info("Received text message with contents: [" + content + "] at " + new Date());

                StringBuffer temp = new StringBuffer();
                temp.append("QpidJMSResponderBean received message with content: [" + content);
                temp.append("] at " + new Date());

                if(message.getJMSReplyTo() != null)
                {
                    connection = _connectionFactory.createConnection();
                    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    messageProducer = session.createProducer(message.getJMSReplyTo());
                    response = session.createTextMessage();
                    response.setText(temp.toString());
                    messageProducer.send(response);
                }
                else
                {
                    _log.warn("Response was requested with no JMSReplyToDestination set. Will not respond to message.");
                }
            }
        }
        catch(Exception e)
        {
            _log.error(e.getMessage(), e);
        }
        finally
        {
            QpidUtil.closeResources(session, connection);
        }
    }
}
