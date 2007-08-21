package org.apache.qpidity.client.impl;

import java.nio.ByteBuffer;

import org.apache.qpidity.ErrorCode;
import org.apache.qpidity.Frame;
import org.apache.qpidity.MessageAcquired;
import org.apache.qpidity.MessageReject;
import org.apache.qpidity.MessageTransfer;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.Range;
import org.apache.qpidity.Session;
import org.apache.qpidity.SessionClosed;
import org.apache.qpidity.SessionDelegate;
import org.apache.qpidity.Struct;
import org.apache.qpidity.client.MessagePartListener;


public class ClientSessionDelegate extends SessionDelegate
{    
    private MessageTransfer _currentTransfer;
    private MessagePartListener _currentMessageListener;
    
    @Override public void sessionClosed(Session ssn,SessionClosed sessionClosed)
    {
        ((ClientSession)ssn).notifyException(new QpidException(sessionClosed.getReplyText(),ErrorCode.get(sessionClosed.getReplyCode()),null));
    }
    
    //  --------------------------------------------
    //   Message methods
    // --------------------------------------------
    @Override public void data(Session ssn, Frame frame)
    {
        for (ByteBuffer b : frame)
        {    
            _currentMessageListener.data(b);
        }
        if (frame.isLastSegment() && frame.isLastFrame())
        {
            _currentMessageListener.messageReceived();
        }
        
    }

    @Override public void headers(Session ssn, Struct... headers)
    {
        _currentMessageListener.messageHeaders(headers);
    }


    @Override public void messageTransfer(Session session, MessageTransfer currentTransfer)
    {
        _currentTransfer = currentTransfer;
        _currentMessageListener = ((ClientSession)session).getMessageListerners().get(currentTransfer.getDestination());
        _currentMessageListener.messageTransfer(currentTransfer.getId());
    }
    
    @Override public void messageReject(Session session, MessageReject struct) 
    {
        for (Range range : struct.getTransfers())
        {
            for (long l = range.getLower(); l <= range.getUpper(); l++)
            {
                System.out.println("message rejected: " +
                        session.getCommand((int) l));
            }
        }
        ((ClientSession)session).setRejectedMessages(struct.getTransfers());
        ((ClientSession)session).notifyException(new QpidException("Message Rejected",ErrorCode.MESSAGE_REJECTED,null));
    }
    
    @Override public void messageAcquired(Session session, MessageAcquired struct) 
    {
        ((ClientSession)session).setAccquiredMessages(struct.getTransfers());
    }
}
