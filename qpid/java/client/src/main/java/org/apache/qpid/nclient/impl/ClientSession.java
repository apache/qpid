package org.apache.qpid.nclient.impl;

import org.apache.qpid.nclient.api.Message;
import org.apache.qpid.nclient.api.MessageReceiver;
import org.apache.qpidity.Header;
import org.apache.qpidity.Option;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.Session;

public class ClientSession extends Session implements org.apache.qpid.nclient.api.Session
{	
	/**
	 * ---------------------------------------------------
	 * Message methods
	 * ---------------------------------------------------
	 */
	/*public MessageSender createSender(String queueName) throws QpidException
	{
		return null;
	}*/

	public MessageReceiver createReceiver(String queueName, Option... options) throws QpidException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public void setTransacted() throws QpidException, IllegalStateException
	{
		// TODO Auto-generated method stub
		
	}

	public void messageBody(byte[] src) throws QpidException
	{
		// TODO Auto-generated method stub
		
	}

	public void messageClose() throws QpidException
	{
		// TODO Auto-generated method stub
		
	}

	public void messageHeaders(Header... headers) throws QpidException
	{
		// TODO Auto-generated method stub
		
	}

	public void messageTransfer(String destination, Message msg) throws QpidException
	{
		// TODO Auto-generated method stub
		
	}

	public void messageTransfer(Option... options) throws QpidException
	{
		// TODO Auto-generated method stub
		
	}	
}
