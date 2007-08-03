package org.apache.qpid.nclient.impl;

import org.apache.qpid.nclient.MessageListener;
import org.apache.qpid.nclient.StreamingMessageListener;
import org.apache.qpidity.Header;
import org.apache.qpidity.Option;
import org.apache.qpidity.api.Message;

public class StreamingListenerAdapter implements StreamingMessageListener
{
	MessageListener _adaptee;
	Message _currentMsg;
	
	public StreamingListenerAdapter(MessageListener l)
	{
		_adaptee = l;
	}

	public void addData(byte[] src)
	{
		_currentMsg.appendData(src);
	}

	public void addMessageHeaders(Header... headers)
	{
		//_currentMsg add the headers
	}

	public void messageTransfer(Message message)
	{
		_adaptee.messageTransfer(_currentMsg);
	}
}
