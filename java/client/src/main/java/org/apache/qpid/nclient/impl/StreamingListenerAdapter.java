package org.apache.qpid.nclient.impl;

import org.apache.qpid.nclient.api.MessageListener;
import org.apache.qpidity.Header;
import org.apache.qpidity.Option;
import org.apache.qpidity.api.Message;
import org.apache.qpidity.api.StreamingMessageListener;

public class StreamingListenerAdapter implements StreamingMessageListener
{
	MessageListener _adaptee;
	Message _currentMsg;
	
	public StreamingListenerAdapter(MessageListener l)
	{
		_adaptee = l;
	}

	public void data(byte[] src)
	{
		_currentMsg.appendData(src);
	}

	public void endData()
	{
		_adaptee.onMessage(_currentMsg);
	}

	public void messageHeaders(Header... headers)
	{
		//_currentMsg add the headers
	}

	public void messageTransfer(String destination, Option... options)
	{
		// _currentMsg create message from factory
	}
}
