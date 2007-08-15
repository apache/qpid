package org.apache.qpidity.client.impl;

import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.qpidity.Option;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.Range;
import org.apache.qpidity.RangeSet;
import org.apache.qpidity.api.Message;
import org.apache.qpidity.client.ExceptionListener;
import org.apache.qpidity.client.MessagePartListener;
import org.apache.qpidity.client.Session;

/**
 * Implements a Qpid Sesion. 
 */
public class ClientSession extends org.apache.qpidity.Session implements org.apache.qpidity.client.Session
{
    private Map<String,MessagePartListener> _messageListeners = new HashMap<String,MessagePartListener>();
    private ExceptionListener _exceptionListner;
    private RangeSet _acquiredMessages;
    private RangeSet _rejectedMessages;
    
    @Override public void sessionClose()
    {
        super.sessionClose();
    }
    
    public void messageAcknowledge(RangeSet ranges)
    {
        for (Range range : ranges)
        {
            for (long l = range.getLower(); l <= range.getUpper(); l++)
            {
                System.out.println("Acknowleding message for : " + super.getCommand((int) l));
                super.processed(l);
            }
        }
    }

    public void messageSubscribe(String queue, String destination, short confirmMode, short acquireMode, MessagePartListener listener, Map<String, ?> filter, Option... options)
    {
        setMessageListener(destination,listener);
        super.messageSubscribe(queue, destination, confirmMode, acquireMode, filter, options);
    }

    public void messageTransfer(String destination, Message msg, short confirmMode, short acquireMode) throws IOException
    {
        // The javadoc clearly says that this method is suitable for small messages
        // therefore reading the content in one shot.
        super.messageTransfer(destination, confirmMode, acquireMode);
        super.headers(msg.getDeliveryProperties(),msg.getMessageProperties());
        super.data(msg.readData());
        super.endData();        
    }
    
    public void messageStream(String destination, Message msg, short confirmMode, short acquireMode) throws IOException
    {
        super.messageTransfer(destination, confirmMode, acquireMode);
        super.headers(msg.getDeliveryProperties(),msg.getMessageProperties());
        boolean b = true;
        int count = 0;
        while(b)
        {   
            try
            {
                System.out.println("count : " + count++);
                super.data(msg.readData());
            }
            catch(EOFException e)
            {
                b = false;
            }
        }   
        
        super.endData();
    }
    
    public RangeSet getAccquiredMessages()
    {
        return _acquiredMessages;
    }

    public RangeSet getRejectedMessages()
    {
        return _rejectedMessages;
    }
    
    public void setMessageListener(String destination, MessagePartListener listener)
    {
        _messageListeners.put(destination, listener);       
    }
    
    public void setExceptionListener(ExceptionListener exceptionListner)
    {
        _exceptionListner = exceptionListner;        
    }   
    
    // ugly but nessacery
    
    void setAccquiredMessages(RangeSet acquiredMessages)
    {
        _acquiredMessages = acquiredMessages;
    }
    
    void setRejectedMessages(RangeSet rejectedMessages)
    {
        _rejectedMessages = rejectedMessages;
    }
    
    void notifyException(QpidException ex)
    {
        _exceptionListner.onException(ex);
    }
    
    Map<String,MessagePartListener> getMessageListerners()
    {
        return _messageListeners;
    }
}
