package org.apache.qpidity.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpidity.Option;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.Range;
import org.apache.qpidity.RangeSet;
import org.apache.qpidity.api.Message;

/**
 * Implements a Qpid Sesion. 
 */
public class ClientSession extends org.apache.qpidity.Session implements org.apache.qpidity.client.Session
{
    private Map<String,MessagePartListener> _messageListeners = new HashMap<String,MessagePartListener>();
    private ExceptionListener _exceptionListner;
    private RangeSet _acquiredMessages;
    private RangeSet _rejectedMessages;
    private Map<String,List<RangeSet>> _unackedMessages = new HashMap<String,List<RangeSet>>();
    
    @Override public void sessionClose()
    {
        // release all unacked messages and then issues a close
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

    public void messageTransfer(String exchange, Message msg, short confirmMode, short acquireMode)
    {
        // need to break it down into small pieces
        super.messageTransfer(exchange, confirmMode, acquireMode);
        super.headers(msg.getDeliveryProperties(),msg.getMessageProperties());
        // super.data(bytes); *
        // super.endData()
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
