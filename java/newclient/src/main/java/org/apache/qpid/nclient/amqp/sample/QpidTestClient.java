package org.apache.qpid.nclient.amqp.sample;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.nclient.api.QpidConnection;
import org.apache.qpid.nclient.api.QpidConstants;
import org.apache.qpid.nclient.api.QpidExchangeHelper;
import org.apache.qpid.nclient.api.QpidMessageConsumer;
import org.apache.qpid.nclient.api.QpidMessageProducer;
import org.apache.qpid.nclient.api.QpidQueueHelper;
import org.apache.qpid.nclient.api.QpidSession;
import org.apache.qpid.nclient.impl.QpidConnectionImpl;
import org.apache.qpid.nclient.message.AMQPApplicationMessage;
import org.apache.qpid.nclient.message.MessageHeaders;

public class QpidTestClient
{
	public static void main(String[] args)
	{
		try
		{
			QpidConnection con = new QpidConnectionImpl();
			con.connect("amqp://guest:guest@test/test?brokerlist='tcp://localhost:5672?'");
			
			QpidSession session = con.createSession(QpidConstants.SESSION_EXPIRY_TIED_TO_CHANNEL);			
			session.open();
			
			QpidExchangeHelper exchangeHelper = session.getExchangeHelper();
			exchangeHelper.declareExchange(false, false, QpidConstants.DIRECT_EXCHANGE_NAME, false, false, false, QpidConstants.DIRECT_EXCHANGE_CLASS);
			
			QpidQueueHelper queueHelper = session.getQueueHelper();
			queueHelper.declareQueue(false, false, false, false, false, "myQueue");
			queueHelper.bindQueue(QpidConstants.DIRECT_EXCHANGE_NAME, false, "myQueue", "RH");
			
			MessageHeaders msgHeaders = new MessageHeaders();
			msgHeaders.setRoutingKey(new AMQShortString("RH"));
			msgHeaders.setExchange(new AMQShortString(QpidConstants.DIRECT_EXCHANGE_NAME));
			AMQPApplicationMessage msg = new AMQPApplicationMessage(msgHeaders,"test".getBytes());
			
			QpidMessageProducer messageProducer = session.createProducer();
			messageProducer.open();
			messageProducer.send(false, true, msg);
			
			QpidMessageConsumer messageConsumer = session.createConsumer("myQueue", false, false);
			messageConsumer.open();
			
			AMQPApplicationMessage msg2 = messageConsumer.receive();
			System.out.println(msg.toString());
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
	}
	
}
