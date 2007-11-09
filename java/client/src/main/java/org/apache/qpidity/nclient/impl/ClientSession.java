package org.apache.qpidity.nclient.impl;

import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.nio.ByteBuffer;

import org.apache.qpidity.transport.Option;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.transport.Range;
import org.apache.qpidity.transport.RangeSet;
import org.apache.qpidity.api.Message;
import org.apache.qpidity.nclient.ClosedListener;
import org.apache.qpidity.nclient.MessagePartListener;

/**
 * Implements a Qpid Sesion. 
 */
public class ClientSession extends org.apache.qpidity.transport.Session implements  org.apache.qpidity.nclient.DtxSession
{
    static
    {
        MAX_NOT_SYNC_DATA_LENGH = 200000 * 1024;
        String max = "message_size_before_sync";       
        if (System.getProperties().containsKey(max))
        {
            try
            {
                MAX_NOT_SYNC_DATA_LENGH = new Long(System.getProperties().getProperty(max));
            }
            catch (NumberFormatException e)
            {
                // use default size
            }
        }
    }

    private static  long MAX_NOT_SYNC_DATA_LENGH  ;
    private Map<String,MessagePartListener> _messageListeners = new HashMap<String,MessagePartListener>();
    private ClosedListener _exceptionListner;
    private RangeSet _acquiredMessages;
    private RangeSet _rejectedMessages;
    private long _dataSentNotSync;


    public void messageAcknowledge(RangeSet ranges)
    {
        for (Range range : ranges)
        {
            super.processed(range);
        }
        super.flushProcessed();
    }

    public void messageSubscribe(String queue, String destination, short confirmMode, short acquireMode, MessagePartListener listener, Map<String, Object> filter, Option... options)
    {
        setMessageListener(destination,listener);
        super.messageSubscribe(queue, destination, confirmMode, acquireMode, filter, options);
    }

    public void messageTransfer(String destination, Message msg, short confirmMode, short acquireMode) throws IOException
    {
        // The javadoc clearly says that this method is suitable for small messages
        // therefore reading the content in one shot.
        ByteBuffer  data = msg.readData();
        _dataSentNotSync = _dataSentNotSync + msg.getMessageProperties().getContentLength() + data.limit();
        super.messageTransfer(destination, confirmMode, acquireMode);
        super.header(msg.getDeliveryProperties(),msg.getMessageProperties());
        super.data( data );
        super.endData();
        if( _dataSentNotSync >= MAX_NOT_SYNC_DATA_LENGH)
        {
            sync();
        }
    }

    public void sync()
    {
        _dataSentNotSync = 0;
        super.sync();
    }

    public void messageStream(String destination, Message msg, short confirmMode, short acquireMode) throws IOException
    {
        super.messageTransfer(destination, confirmMode, acquireMode);
        super.header(msg.getDeliveryProperties(),msg.getMessageProperties());
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
        if (listener == null)
        {
            throw new IllegalArgumentException("Cannot set message listener to null");
        }
        _messageListeners.put(destination, listener);       
    }
    
    public void setClosedListener(ClosedListener exceptionListner)
    {
        _exceptionListner = exceptionListner;        
    }   
    
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
        _exceptionListner.onClosed(null, null);
    }
    
    Map<String,MessagePartListener> getMessageListerners()
    {
        return _messageListeners;
    }
}
