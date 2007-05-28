package org.apache.qpid.nclient.message;

import org.apache.qpid.nclient.core.AMQPException;

public interface MessageStore {
	
    public void removeMessage(String identifier) throws AMQPException;
	
	public void storeContentBodyChunk(String identifier,byte[] contentBody) throws AMQPException;

    public void storeMessageMetaData(String identifier, MessageHeaders messageHeaders) throws AMQPException;

    public AMQPApplicationMessage getMessage(String identifier) throws AMQPException;
    
    public void storeMessage(String identifier,AMQPApplicationMessage message)throws AMQPException;
}
