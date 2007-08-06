package org.apache.qpidity.impl;

import org.apache.qpidity.MessagePartListener;
import org.apache.qpidity.MessageListener;
import org.apache.qpidity.Header;
import org.apache.qpidity.api.Message;

public class MessagePartListenerAdapter implements MessagePartListener
{
	MessageListener _adaptee;
	Message _currentMsg;
	
	public MessagePartListenerAdapter(MessageListener listener)
	{
		_adaptee = listener;
        _currentMsg = null;
    }

	public void addData(byte[] src)
	{
		_currentMsg.appendData(src);
	}

	public void messageHeaders(Header... headers)
	{
		//_currentMsg add the headers
	}

	public void messageReceived()
	{
		_adaptee.onMessage(_currentMsg);
	}
}
