package org.apache.qpid.nclient.impl;

import java.nio.ByteBuffer;

import org.apache.qpid.ErrorCode;

import org.apache.qpid.nclient.MessagePartListener;

import org.apache.qpid.QpidException;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageReject;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Range;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionDetached;
import org.apache.qpid.transport.SessionDelegate;


public class ClientSessionDelegate extends SessionDelegate
{

    //  --------------------------------------------
    //   Message methods
    // --------------------------------------------
    @Override public void messageTransfer(Session session, MessageTransfer xfr)
    {
        MessagePartListener listener = ((ClientSession)session).getMessageListeners()
            .get(xfr.getDestination());
        listener.messageTransfer(xfr);
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
