package org.apache.qpid.nclient.amqp.sample;

import org.apache.qpid.nclient.api.QpidConnection;
import org.apache.qpid.nclient.api.QpidConstants;
import org.apache.qpid.nclient.api.QpidExchangeHelper;
import org.apache.qpid.nclient.api.QpidMessageProducer;
import org.apache.qpid.nclient.api.QpidQueueHelper;
import org.apache.qpid.nclient.api.QpidSession;
import org.apache.qpid.nclient.impl.QpidConnectionImpl;

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
			exchangeHelper.open();
			exchangeHelper.declareExchange(false, false, QpidConstants.DIRECT_EXCHANGE_NAME, false, false, false, QpidConstants.DIRECT_EXCHANGE_CLASS);
			
			QpidQueueHelper queueHelper = session.getQueueHelper();
			queueHelper.open();
			queueHelper.declareQueue(false, false, false, false, false, "myQueue");
			queueHelper.bindQueue(QpidConstants.DIRECT_EXCHANGE_NAME, false, "myQueue", "RH");
			
			QpidMessageProducer messageProducer = session.createProducer();
		}
		catch(Exception e)
		{
			
		}
		
	}
}
