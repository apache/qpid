package org.apache.qpid.nclient.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpidity.api.Message;
import org.apache.qpid.nclient.MessagePartListener;
import org.apache.qpidity.*;

/**
 * Implements a Qpid Sesion. 
 */
public class ClientSession implements org.apache.qpid.nclient.Session
{

	Map<String,MessagePartListener> messagListeners = new HashMap<String,MessagePartListener>();


    //------------------------------------------------------
    //                 Session housekeeping methods
    //------------------------------------------------------
    public void close() throws QpidException
    {
        // TODO

    }

    public void suspend() throws QpidException
    {
        // TODO

    }

    public void resume() throws QpidException
    {
        // TODO

    }//------------------------------------------------------
    //                 Messaging methods
    //                   Producer
    //------------------------------------------------------
    public void messageTransfer(String exchange, Message msg, short confirmMode, short acquireMode) throws QpidException
    {
        // TODO

    }

    public void messageTransfer(String exchange, short confirmMode, short acquireMode) throws QpidException
    {
        // TODO

    }

    public void addMessageHeaders(Header... headers) throws QpidException
    {
        // TODO

    }

    public void addData(byte[] data, int off, int len) throws QpidException
    {
        // TODO

    }

    public void endData() throws QpidException
    {
        // TODO

    }

    public void messageSubscribe(String queue, String destination, short confirmMode, short acquireMode,
                                 MessagePartListener listener, Map<String, ?> filter, Option... options)
            throws QpidException
    {
        // TODO

    }

    public void messageCancel(String destination) throws QpidException
    {
        // TODO

    }

    public void setMessageListener(String destination, MessagePartListener listener)
    {
        // TODO

    }

    public void messageFlowMode(String destination, short mode) throws QpidException
    {
        // TODO

    }

    public void messageFlow(String destination, short unit, long value) throws QpidException
    {
        // TODO

    }

    public boolean messageFlush(String destination) throws QpidException
    {
        // TODO
        return false;
    }

    public void messageStop(String destination) throws QpidException
    {
        // TODO

    }

    public void messageAcknowledge(Range<Long>... range) throws QpidException
    {
        // TODO

    }

    public void messageReject(Range<Long>... range) throws QpidException
    {
        // TODO

    }

    public Range<Long>[] messageAcquire(Range<Long>... range) throws QpidException
    {
        // TODO
        return null;
    }

    public void messageRelease(Range<Long>... range) throws QpidException
    {
        // TODO

    }// -----------------------------------------------
    //            Local transaction methods
    //  ----------------------------------------------
    public void txSelect() throws QpidException
    {
        // TODO

    }

    public void txCommit() throws QpidException, IllegalStateException
    {
        // TODO

    }

    public void txRollback() throws QpidException, IllegalStateException
    {
        // TODO

    }

    public void queueDeclare(String queueName, String alternateExchange, Map<String, ?> arguments, Option... options)
            throws QpidException
    {
        // TODO

    }

    public void queueBind(String queueName, String exchangeName, String routingKey, Map<String, ?> arguments)
            throws QpidException
    {
        // TODO

    }

    public void queueUnbind(String queueName, String exchangeName, String routingKey, Map<String, ?> arguments)
            throws QpidException
    {
        // TODO

    }

    public void queuePurge(String queueName) throws QpidException
    {
        // TODO

    }

    public void queueDelete(String queueName, Option... options) throws QpidException
    {
        // TODO

    }

    public void exchangeDeclare(String exchangeName, String exchangeClass, String alternateExchange,
                                Map<String, ?> arguments, Option... options) throws QpidException
    {
        // TODO

    }

    public void exchangeDelete(String exchangeName, Option... options) throws QpidException
    {
        // TODO

    }
}
