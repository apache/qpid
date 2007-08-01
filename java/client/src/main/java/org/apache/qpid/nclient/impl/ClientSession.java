package org.apache.qpid.nclient.impl;

import java.util.Map;

import org.apache.qpid.nclient.api.MessageListener;
import org.apache.qpidity.Option;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.Session;

public class ClientSession extends Session implements org.apache.qpid.nclient.api.Session
{	
	public void addMessageListener(String destination,MessageListener listener)
	{
		super.addMessageListener(destination, new StreamingListenerAdapter(listener));
	}
	
	//temproary until rafi updates the xml when the new message stuff is voted in.
	public void messageSubscribe(String queue, String destination, Map<String, ?> filter, Option... _options) throws QpidException
	{
		// TODO Auto-generated method stub		
	}
}
