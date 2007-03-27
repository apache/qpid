package org.apache.qpid.nclient.message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.message.MessageHeaders;

public class TransientMessageStore implements MessageStore {

	private Map<String,AMQPApplicationMessage> messageMap = new ConcurrentHashMap<String,AMQPApplicationMessage>();
	
	public AMQPApplicationMessage getMessage(String identifier)
			throws AMQException 
	{
		return messageMap.get(identifier);		
	}

	public void removeMessage(String identifier) 
	{
		messageMap.remove(identifier);
	}

	public void storeContentBodyChunk(String identifier, byte[] contentBody)
			throws AMQException 
	{
		
	}

	public void storeMessageMetaData(String identifier,
			MessageHeaders messageHeaders) throws AMQException 
	{
		
	}

	public void storeMessage(String identifier,AMQPApplicationMessage message)throws AMQException
	{
		
	}
}
