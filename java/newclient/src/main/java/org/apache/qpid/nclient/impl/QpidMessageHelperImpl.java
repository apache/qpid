package org.apache.qpid.nclient.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.qpid.framing.Content;
import org.apache.qpid.framing.MessageAppendBody;
import org.apache.qpid.framing.MessageCheckpointBody;
import org.apache.qpid.framing.MessageCloseBody;
import org.apache.qpid.framing.MessageOpenBody;
import org.apache.qpid.framing.MessageRecoverBody;
import org.apache.qpid.framing.MessageResumeBody;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.nclient.amqp.AMQPMessage;
import org.apache.qpid.nclient.amqp.AMQPMessageCallBack;
import org.apache.qpid.nclient.api.QpidException;
import org.apache.qpid.nclient.api.QpidMessageConsumer;
import org.apache.qpid.nclient.api.QpidMessageHelper;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.message.AMQPApplicationMessage;
import org.apache.qpid.nclient.message.MessageHeaders;
import org.apache.qpid.nclient.message.MessageStore;
import org.apache.qpid.nclient.message.TransientMessageStore;

public class QpidMessageHelperImpl extends AbstractResource implements QpidMessageHelper, AMQPMessageCallBack
{
	private MessageStore _msgStore = new TransientMessageStore();
	private Map<String,QpidMessageConsumer> _consumers = new ConcurrentHashMap<String,QpidMessageConsumer>();
	private QpidSessionImpl _session;
	private AMQPMessage _amqpMessage;
	
	protected QpidMessageHelperImpl(QpidSessionImpl session)
	{
		super("Message Class");
		_session = session;		
	}
		
	/**
	 * -----------------------------------------------
	 * Abstract methods from AbstractResource class
	 * -----------------------------------------------
	 */
	protected void openResource() throws AMQPException, QpidException
	{
		_amqpMessage = _session.getClassFactory().createMessageClass(_session.getChannel(),this);
	}
	
	protected void closeResource() throws AMQPException, QpidException
	{
		_session.getClassFactory().destoryMessageClass(_session.getChannel(), _amqpMessage);
		for (String consumerTag : _consumers.keySet())
		{
			QpidMessageConsumer consumer = _consumers.get(consumerTag);
			// The close method will deregister itself too.
			consumer.close();
		}
	}
	
	/**
	 * -----------------------------------------------
	 * methods from QpidMessageHelper class
	 * -----------------------------------------------
	 */
	public AMQPMessage getMessageClass() throws QpidException
	{
		return _amqpMessage;
	}

	/**
	 * -----------------------------------------------
	 * Methods from AMQPMessageCallback class
	 * -----------------------------------------------
	 */
	public void append(MessageAppendBody messageAppendBody, long correlationId) throws AMQPException
	{ 
		String reference = new String(messageAppendBody.getReference());
		AMQPApplicationMessage msg = _msgStore.getMessage(reference);
		msg.addContent(messageAppendBody.getBytes());	
	}

	public void checkpoint(MessageCheckpointBody messageCheckpointBody, long correlationId) throws AMQPException
	{
		// TODO Auto-generated method stub
	}

	public void close(MessageCloseBody messageCloseBody, long correlationId) throws AMQPException
	{
		String reference = new String(messageCloseBody.getReference());
		AMQPApplicationMessage msg = _msgStore.getMessage(reference);
		notifyMessageArrival(msg);
		_msgStore.removeMessage(reference);
	}

	public void open(MessageOpenBody messageOpenBody, long correlationId) throws AMQPException
	{
		String reference = new String(messageOpenBody.getReference());
		AMQPApplicationMessage msg = new AMQPApplicationMessage(_session.getChannel(), messageOpenBody.getReference());
		_msgStore.storeMessage(reference, msg);
	}

	public void recover(MessageRecoverBody messageRecoverBody, long correlationId) throws AMQPException
	{
		// TODO Auto-generated method stub

	}

	public void resume(MessageResumeBody messageResumeBody, long correlationId) throws AMQPException
	{
		// TODO Auto-generated method stub

	}

	public void transfer(MessageTransferBody messageTransferBody, long correlationId) throws AMQPException
	{
		MessageHeaders messageHeaders = new MessageHeaders();
        messageHeaders.setMessageId(messageTransferBody.getMessageId());
        messageHeaders.setAppId(messageTransferBody.getAppId());
        messageHeaders.setContentType(messageTransferBody.getContentType());
        messageHeaders.setEncoding(messageTransferBody.getContentEncoding());
        messageHeaders.setCorrelationId(messageTransferBody.getCorrelationId());
        messageHeaders.setDestination(messageTransferBody.getDestination());
        messageHeaders.setExchange(messageTransferBody.getExchange());
        messageHeaders.setExpiration(messageTransferBody.getExpiration());
        messageHeaders.setReplyTo(messageTransferBody.getReplyTo());
        messageHeaders.setRoutingKey(messageTransferBody.getRoutingKey());
        messageHeaders.setTransactionId(messageTransferBody.getTransactionId());
        messageHeaders.setUserId(messageTransferBody.getUserId());
        messageHeaders.setPriority(messageTransferBody.getPriority());
        messageHeaders.setDeliveryMode(messageTransferBody.getDeliveryMode());
        messageHeaders.setApplicationHeaders(messageTransferBody.getApplicationHeaders());
		
        
        
		if (messageTransferBody.getBody().getContentType() == Content.TypeEnum.INLINE_T)
		{
			AMQPApplicationMessage msg = new AMQPApplicationMessage(_session.getChannel(), 
																	messageTransferBody.getDestination().asString(),
                                                                    messageHeaders,
                                                                    messageTransferBody.getBody().getContentAsByteArray(), 
                                                                    messageTransferBody.getRedelivered());
			
			notifyMessageArrival(msg);
		}
		else
		{
			byte[] referenceId = messageTransferBody.getBody().getContentAsByteArray();
			AMQPApplicationMessage msg = new AMQPApplicationMessage(_session.getChannel(),referenceId);
			msg.setMessageHeaders(messageHeaders);
			msg.setRedeliveredFlag(messageTransferBody.getRedelivered());
			
			_msgStore.storeMessage(new String(referenceId), msg);
		}
	}
	
	/** -----------------------------------------
	 * Methods defined by this class
	 * ------------------------------------------
	 */
	
	public void registerConsumer(String consumerTag,QpidMessageConsumer messageConsumer)
	{
		_consumers.put(consumerTag, messageConsumer);
	}

	public void deregisterConsumer(String consumerTag)
	{
		if(_consumers.containsKey(consumerTag))
		{
			_consumers.remove(consumerTag);
		}
		// If it's not their no need to worry (or raise an exception)
	}
	
	private void notifyMessageArrival(AMQPApplicationMessage msg)
	{
		QpidMessageConsumer consumer = _consumers.get(msg.getDeliveryTag());
		try
		{
			consumer.messageArrived(msg);
		}
		catch(QpidException e)
		{
			// maybe retry and then reject the message
		}
	}
}
