package org.apache.qpid.nclient.impl;

import java.util.Map;

import org.apache.qpidity.api.StreamingMessageListener;
import org.apache.qpid.nclient.api.MessageListener;
import org.apache.qpidity.Option;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.Session;

public class ClientSession extends Session implements org.apache.qpid.nclient.api.Session
{

    public void setMessageListener(String destination,MessageListener listener)
    {
        super.setMessageListener(destination, new StreamingListenerAdapter(listener));
    }

    public void messageSubscribe(String queue, String destination, Map<String, ?> filter, Option... _options) throws QpidException
    {
        // TODO
    }

    public void messageSubscribe(String queue, String destination, Map<String, ?> filter, StreamingMessageListener listener, Option... _options) throws QpidException
    {
        // TODO
    }

}
