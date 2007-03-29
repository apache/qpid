package org.apache.qpid.nclient.amqp.sample;

import org.apache.qpid.framing.MessageAppendBody;
import org.apache.qpid.framing.MessageCheckpointBody;
import org.apache.qpid.framing.MessageCloseBody;
import org.apache.qpid.framing.MessageOpenBody;
import org.apache.qpid.framing.MessageRecoverBody;
import org.apache.qpid.framing.MessageResumeBody;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.nclient.amqp.AMQPMessageCallBack;
import org.apache.qpid.nclient.core.AMQPException;

public class MessageHelper implements AMQPMessageCallBack
{

    public void append(MessageAppendBody messageAppendBody, long correlationId) throws AMQPException
    {
	// TODO Auto-generated method stub

    }

    public void checkpoint(MessageCheckpointBody messageCheckpointBody, long correlationId) throws AMQPException
    {
	// TODO Auto-generated method stub

    }

    public void close(MessageCloseBody messageCloseBody, long correlationId) throws AMQPException
    {
	// TODO Auto-generated method stub

    }

    public void open(MessageOpenBody messageOpenBody, long correlationId) throws AMQPException
    {
	// TODO Auto-generated method stub

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
	System.out.println("The Broker has sent a message" + messageTransferBody.toString());
    }

}
