package org.apache.qpid.nclient.message;

import org.apache.qpid.AMQException;

public interface MessageStore {
	
    public void removeMessage(String identifier);
	
	public void storeContentBodyChunk(String identifier,byte[] contentBody) throws AMQException;

    public void storeMessageMetaData(String identifier, MessageHeaders messageHeaders) throws AMQException;

    public AMQPApplicationMessage getMessage(String identifier) throws AMQException;
    
    public void storeMessage(String identifier,AMQPApplicationMessage message)throws AMQException;
}
