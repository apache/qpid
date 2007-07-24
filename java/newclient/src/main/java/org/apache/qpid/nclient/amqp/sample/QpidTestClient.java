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
			
			exchangeHelper.declareExchange(false, false, QpidConstants.SYNAPSE_EXCHANGE_NAME, false, false, false, QpidConstants.SYNAPSE_EXCHANGE_CLASS);
								
		    //contentBasedRoutingSample(session);
			
			//transformationSample2(session);			
			
			binaryMessageTransformations(session);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
	}
	
	public static void contentBasedRoutingSample(QpidSession session) throws Exception
	{
		String tmp = "<m:troubleTicket xmlns:m=\"http://redhat.com/sample\"><m:customerId>532535</m:customerId><m:priority>";
		String tmp2 = "</m:priority><m:appId>ESB</m:appId><m:desc>blabla</m:desc></m:troubleTicket>";
		
		//Create queues
		QpidQueueHelper queueHelper = session.getQueueHelper();
		queueHelper.declareQueue(false, false, false, false, false, "criticalTicketQueue");
		queueHelper.bindQueue(QpidConstants.DIRECT_EXCHANGE_NAME, false, "criticalTicketQueue", "criticalTicket");
		
		queueHelper.declareQueue(false, false, false, false, false, "lowPriorityTicketQueue");
		queueHelper.bindQueue(QpidConstants.DIRECT_EXCHANGE_NAME, false, "lowPriorityTicketQueue", "lowPriorityTicket");
		
		queueHelper.declareQueue(false, false, false, false, false, "ticketQueue");
		queueHelper.bindQueue(QpidConstants.DIRECT_EXCHANGE_NAME, false, "ticketQueue", "troubleTicket");
		
		QpidMessageProducer messageProducer = session.createProducer();
		messageProducer.open();
				
		MessageHeaders msgHeaders = new MessageHeaders();
		msgHeaders.setContentType(new AMQShortString("text/xml"));
		msgHeaders.setRoutingKey(new AMQShortString("defectSystem"));
		msgHeaders.setDestination(new AMQShortString(QpidConstants.SYNAPSE_EXCHANGE_NAME));
		msgHeaders.setExchange(new AMQShortString(QpidConstants.SYNAPSE_EXCHANGE_NAME));
				
		StringBuffer buf = new StringBuffer();
		buf.append(tmp).append("critical").append(tmp2);
		AMQPApplicationMessage criticalMsg = new AMQPApplicationMessage(msgHeaders,buf.toString().getBytes());
		System.out.println(criticalMsg.toString());
		messageProducer.send(false, true, criticalMsg);
		
		buf = new StringBuffer();
		buf.append(tmp).append("low").append(tmp2);
		AMQPApplicationMessage lowMsg = new AMQPApplicationMessage(msgHeaders,buf.toString().getBytes());
		System.out.println(lowMsg.toString());
		messageProducer.send(false, true, lowMsg);
		
		buf = new StringBuffer();
		buf.append(tmp).append("high").append(tmp2);
		AMQPApplicationMessage highMsg = new AMQPApplicationMessage(msgHeaders,buf.toString().getBytes());
		System.out.println(highMsg.toString());
		messageProducer.send(false, true, highMsg);
		
		QpidMessageConsumer messageConsumerCritical = session.createConsumer("criticalTicketQueue", false, false);
		messageConsumerCritical.open();		
		AMQPApplicationMessage criticalMsgRcv = messageConsumerCritical.receive();
		System.out.println(criticalMsgRcv.toString());
		
		QpidMessageConsumer messageConsumerLow = session.createConsumer("lowPriorityTicketQueue", false, false);
		messageConsumerLow.open();		
		AMQPApplicationMessage lowMsgRcv = messageConsumerLow.receive();
		System.out.println(lowMsgRcv.toString());
		
		QpidMessageConsumer messageConsumer = session.createConsumer("ticketQueue", false, false);
		messageConsumer.open();		
		AMQPApplicationMessage msgRcv = messageConsumer.receive();
		System.out.println(msgRcv.toString());
		
	}
	
	public static void transformationSample(QpidSession session) throws Exception
	{
		String tmp = "<m:quote xmlns:m=\"http://redhat.com/sample\"><m:ticker>RHT</m:ticker><m:value>125</m:value></m:quote>";
		
		QpidQueueHelper queueHelper = session.getQueueHelper();
		queueHelper.declareQueue(false, false, false, false, false, "stockQuoteQueue");
		queueHelper.bindQueue(QpidConstants.DIRECT_EXCHANGE_NAME, false, "stockQuoteQueue", "stockQuote");
		
		MessageHeaders msgHeaders = new MessageHeaders();
		msgHeaders.setContentType(new AMQShortString("text/xml"));
		msgHeaders.setRoutingKey(new AMQShortString("stockQuote"));
		msgHeaders.setDestination(new AMQShortString(QpidConstants.SYNAPSE_EXCHANGE_NAME));
		msgHeaders.setExchange(new AMQShortString(QpidConstants.SYNAPSE_EXCHANGE_NAME));
		
		AMQPApplicationMessage msg = new AMQPApplicationMessage(msgHeaders,tmp.getBytes());
		
		QpidMessageProducer messageProducer = session.createProducer();
		messageProducer.open();
		System.out.println(msg.toString());
		messageProducer.send(false, true, msg);
	}
	
	public static void transformationSample2(QpidSession session) throws Exception
	{
		StringBuffer buf = new StringBuffer();
		    buf.append("<m:vacationPackage xmlns:m=\"http://redhat.com/sample\">"); 
			buf.append("<m:customerFirstName>Rajith</m:customerFirstName>"); 
			buf.append("<m:customerLastName>Rajith</m:customerLastName>"); 
			buf.append("<m:customerAddress>3349 Missississauga Road,Mississauga,ON, L5L 1J7</m:customerAddress>");   
			buf.append("<m:customerDOB>Mississauga</m:customerDOB>");
			buf.append("<m:paymentInfo>Visa,456454574575325325235,05122007</m:paymentInfo>");
			buf.append("<m:start>12072007</m:start>");
			buf.append("<m:end>18072007</m:end>");
			buf.append("<m:airTicket>");     	
			buf.append("<m:airline>AC</m:airline>");
			buf.append("<m:seatPreference>W</m:seatPreference>");
			buf.append("<m:frequentFlyer>643663345</m:frequentFlyer>");
			buf.append("</m:airTicket>"); 
			buf.append("<m:Hotel>");
			buf.append("<m:noOfDays>5</m:noOfDays>");
			buf.append("<m:rating>5</m:rating>");
			buf.append("<m:meals>AI</m:meals>");   
			buf.append("</m:Hotel>");
			buf.append("<m:carRental>");
			buf.append("<m:from>14062007</m:from>");
			buf.append("<m:to>16062007</m:to>");
			buf.append("</m:carRental>");
			buf.append("</m:vacationPackage>");
		
		QpidQueueHelper queueHelper = session.getQueueHelper();
		queueHelper.declareQueue(false, false, false, false, false, "carRentalQueue");
		queueHelper.bindQueue(QpidConstants.DIRECT_EXCHANGE_NAME, false, "carRentalQueue", "carRental");
		
		queueHelper.declareQueue(false, false, false, false, false, "hotelQueue");
		queueHelper.bindQueue(QpidConstants.DIRECT_EXCHANGE_NAME, false, "hotelQueue", "hotel");
		
		queueHelper.declareQueue(false, false, false, false, false, "airlineQueue");
		queueHelper.bindQueue(QpidConstants.DIRECT_EXCHANGE_NAME, false, "airlineQueue", "airline");
		
		MessageHeaders msgHeaders = new MessageHeaders();
		msgHeaders.setContentType(new AMQShortString("text/xml"));
		msgHeaders.setRoutingKey(new AMQShortString("vacationPackage"));
		msgHeaders.setDestination(new AMQShortString(QpidConstants.SYNAPSE_EXCHANGE_NAME));
		msgHeaders.setExchange(new AMQShortString(QpidConstants.SYNAPSE_EXCHANGE_NAME));
		
		AMQPApplicationMessage msg = new AMQPApplicationMessage(msgHeaders,buf.toString().getBytes());
		
		QpidMessageProducer messageProducer = session.createProducer();
		messageProducer.open();
		System.out.println(msg.toString());
		messageProducer.send(false, true, msg);
	}
	
	public static void binaryMessageTransformations(QpidSession session) throws Exception
	{
		QpidQueueHelper queueHelper = session.getQueueHelper();
		queueHelper.declareQueue(false, false, false, false, false, "binaryQueue");
		queueHelper.bindQueue(QpidConstants.DIRECT_EXCHANGE_NAME, false, "binaryQueue", "binary");
		
		MessageHeaders msgHeaders = new MessageHeaders();
		msgHeaders.setContentType(new AMQShortString("application/octet-stream"));
		msgHeaders.setRoutingKey(new AMQShortString("binary"));
		msgHeaders.setDestination(new AMQShortString(QpidConstants.SYNAPSE_EXCHANGE_NAME));
		msgHeaders.setExchange(new AMQShortString(QpidConstants.SYNAPSE_EXCHANGE_NAME));
        
		byte[] buf = new byte[]{72,101,108,108,111};
		
		AMQPApplicationMessage msg = new AMQPApplicationMessage(msgHeaders,buf);
		
		QpidMessageProducer messageProducer = session.createProducer();
		messageProducer.open();
		System.out.println(msg.toString());
		messageProducer.send(false, true, msg);		
	}
	
}
