package org.apache.qpid.nclient.impl;

import java.nio.ByteBuffer;

import org.apache.qpid.ErrorCode;

import org.apache.qpid.nclient.MessagePartListener;

import org.apache.qpid.QpidException;
import org.apache.qpid.transport.Data;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageReject;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Range;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionDetached;
import org.apache.qpid.transport.SessionDelegate;


public class ClientSessionDelegate extends SessionDelegate
{    
    private MessageTransfer _currentTransfer;
    private MessagePartListener _currentMessageListener;
    
    @Override public void sessionDetached(Session ssn, SessionDetached dtc)
    {
        ((ClientSession)ssn).notifyException(new QpidException("", ErrorCode.get(dtc.getCode().getValue()),null));
    }
    
    //  --------------------------------------------
    //   Message methods
    // --------------------------------------------
    @Override public void data(Session ssn, Data data)
    {
        _currentMessageListener.data(data.getData());
        if (data.isLast())
        {
            _currentMessageListener.messageReceived();
        }
    }

    @Override public void header(Session ssn, Header header)
    {
        _currentMessageListener.messageHeader(header);
        if( header.hasNoPayload())
        {
           _currentMessageListener.data(ByteBuffer.allocate(0));
           _currentMessageListener.messageReceived();
        }
    }


    @Override public void messageTransfer(Session session, MessageTransfer currentTransfer)
    {
        _currentTransfer = currentTransfer;
        _currentMessageListener = ((ClientSession)session).getMessageListeners().get(currentTransfer.getDestination());
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
        session.processed(struct);
    }

}
