package org.apache.qpid.nclient.impl;

import java.util.Map;

import org.apache.qpidity.api.Message;
import org.apache.qpid.nclient.MessageListener;
import org.apache.qpidity.*;

/**
 * Implements a Qpid Sesion. 
 */
public class ClientSession implements org.apache.qpid.nclient.Session
{

    //------------------------------------------------------
    //                 Session housekeeping methods
    //------------------------------------------------------
    public void close() throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void suspend() throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void resume() throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }//------------------------------------------------------
    //                 Messaging methods
    //                   Producer
    //------------------------------------------------------
    public void messageTransfer(String exchange, Message msg, Option... options) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void messageTransfer(String exchange, Option... options) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void addMessageHeaders(Header... headers) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void addData(byte[] data, int off, int len) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void endData() throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void messageSubscribe(String queue, String destination, MessageListener listener, Map<String, ?> filter,
                                 Option... options) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void messageCancel(String destination) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setMessageListener(String destination, MessageListener listener)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void messageAcknowledge(Range... range) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void messageReject(Range... range) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Range[] messageAcquire(Range... range) throws QpidException
    {
        return new Range[0];  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void messageRelease(Range... range) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }// -----------------------------------------------
    //            Local transaction methods
    //  ----------------------------------------------
    public void txSelect() throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void txCommit() throws QpidException, IllegalStateException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void txRollback() throws QpidException, IllegalStateException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void queueDeclare(String queueName, String alternateExchange, Map<String, ?> arguments,
                             Option... options) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void queueBind(String queueName, String exchangeName, String routingKey, Map<String, ?> arguments) throws
                                                                                                              QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void queueUnbind(String queueName, String exchangeName, String routingKey, Map<String, ?> arguments) throws
                                                                                                                QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void queuePurge(String queueName) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void queueDelete(String queueName, Option... options) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void exchangeDeclare(String exchangeName, String exchangeClass, String alternateExchange,
                                Map<String, ?> arguments, Option... options) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void exchangeDelete(String exchangeName, Option... options) throws QpidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
