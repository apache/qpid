package org.apache.qpid.nclient.message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.qpid.nclient.core.AMQPException;

public class TransientMessageStore implements MessageStore {

	private Map<String,AMQPApplicationMessage> _messageMap = new ConcurrentHashMap<String,AMQPApplicationMessage>();
	
	public AMQPApplicationMessage getMessage(String identifier)
			throws AMQPException 
	{
		if (!_messageMap.containsKey(identifier))
		{
			throw new AMQPException("identifier not found " + identifier);
		}
		
		return _messageMap.get(identifier);		
	}

	public void removeMessage(String identifier) throws AMQPException
	{
		if (!_messageMap.containsKey(identifier))
		{
			throw new AMQPException("identifier not found " + identifier);
		}
		_messageMap.remove(identifier);
	}

	public void storeContentBodyChunk(String identifier, byte[] contentBody)
			throws AMQPException 
	{
		AMQPApplicationMessage msg = _messageMap.get(identifier);
		msg.addContent(contentBody);
	}

	public void storeMessageMetaData(String identifier,
			MessageHeaders messageHeaders) throws AMQPException 
	{
		AMQPApplicationMessage msg = _messageMap.get(identifier);
		msg.setMessageHeaders(messageHeaders);
	}

	public void storeMessage(String identifier,AMQPApplicationMessage msg)throws AMQPException
	{
		_messageMap.put(identifier, msg);
	}
}
