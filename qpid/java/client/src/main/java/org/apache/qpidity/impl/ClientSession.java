package org.apache.qpidity.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpidity.api.Message;
import org.apache.qpidity.client.ExceptionListener;
import org.apache.qpidity.client.MessagePartListener;
import org.apache.qpidity.*;

/**
 * Implements a Qpid Sesion. 
 */
public class ClientSession implements org.apache.qpidity.client.Session
{

	Map<String,MessagePartListener> messagListeners = new HashMap<String,MessagePartListener>();

    public void addData(byte[] data, int off, int len)
    {
        // TODO Auto-generated method stub
        
    }

    public void addMessageHeaders(Header... headers)
    {
        // TODO Auto-generated method stub
        
    }

    public void close()
    {
        // TODO Auto-generated method stub
        
    }

    public void endData()
    {
        // TODO Auto-generated method stub
        
    }

    public void exchangeDeclare(String exchangeName, String exchangeClass, String alternateExchange, Map<String, ?> arguments, Option... options)
    {
        // TODO Auto-generated method stub
        
    }

    public void exchangeDelete(String exchangeName, Option... options)
    {
        // TODO Auto-generated method stub
        
    }

    public void messageAcknowledge(RangeSet ranges)
    {
        // TODO Auto-generated method stub
        
    }

    public void messageAcquire(RangeSet ranges)
    {
        // TODO Auto-generated method stub
    }

    public void messageCancel(String destination)
    {
        // TODO Auto-generated method stub
        
    }

    public void messageFlow(String destination, short unit, long value)
    {
        // TODO Auto-generated method stub
        
    }

    public void messageFlowMode(String destination, short mode)
    {
        // TODO Auto-generated method stub
        
    }

    public void messageFlush(String destination)
    {
        // TODO Auto-generated method stub
    }

    public void messageReject(RangeSet ranges)
    {
        // TODO Auto-generated method stub
        
    }

    public void messageRelease(RangeSet ranges)
    {
        // TODO Auto-generated method stub
        
    }

    public void messageStop(String destination)
    {
        // TODO Auto-generated method stub
        
    }

    public void messageSubscribe(String queue, String destination, short confirmMode, short acquireMode, MessagePartListener listener, Map<String, ?> filter, Option... options)
    {
        // TODO Auto-generated method stub
        
    }

    public void messageTransfer(String exchange, Message msg, short confirmMode, short acquireMode)
    {
        // TODO Auto-generated method stub
        
    }

    public void messageTransfer(String exchange, short confirmMode, short acquireMode)
    {
        // TODO Auto-generated method stub
        
    }

    public void queueBind(String queueName, String exchangeName, String routingKey, Map<String, ?> arguments)
    {
        // TODO Auto-generated method stub
        
    }

    public void queueDeclare(String queueName, String alternateExchange, Map<String, ?> arguments, Option... options)
    {
        // TODO Auto-generated method stub
        
    }

    public void queueDelete(String queueName, Option... options)
    {
        // TODO Auto-generated method stub
        
    }

    public void queuePurge(String queueName)
    {
        // TODO Auto-generated method stub
        
    }

    public void queueUnbind(String queueName, String exchangeName, String routingKey, Map<String, ?> arguments)
    {
        // TODO Auto-generated method stub
        
    }

    public void resume()
    {
        // TODO Auto-generated method stub
        
    }

    public void setExceptionListener(ExceptionListener exceptionListner)
    {
        // TODO Auto-generated method stub
        
    }

    public void setMessageListener(String destination, MessagePartListener listener)
    {
        // TODO Auto-generated method stub
        
    }

    public void suspend()
    {
        // TODO Auto-generated method stub
        
    }

    public void sync()
    {
        // TODO Auto-generated method stub
        
    }

    public void txCommit() throws IllegalStateException
    {
        // TODO Auto-generated method stub
        
    }

    public void txRollback() throws IllegalStateException
    {
        // TODO Auto-generated method stub
        
    }

    public void txSelect()
    {
        // TODO Auto-generated method stub
        
    }

    public RangeSet getAccquiredMessages()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public int getNoOfUnAckedMessages()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    
}
