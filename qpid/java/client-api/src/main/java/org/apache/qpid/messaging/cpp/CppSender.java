package org.apache.qpid.messaging.cpp;

import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.Sender;
import org.apache.qpid.messaging.Session;

public class CppSender implements Sender
{
    org.apache.qpid.messaging.cpp.jni.Sender _cppSender;
    
    public CppSender(org.apache.qpid.messaging.cpp.jni.Sender cppSender)
    {
        _cppSender = cppSender;
    }

    @Override
    public void send(Message message, boolean sync)
    {
        _cppSender.send(((TextMessage)message).getCppMessage(),true);
    }

    @Override
    public void close()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void setCapacity(int capacity)
    {
        //_cppSender.setCapacity(arg0)
    }

    @Override
    public int getCapacity()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getAvailable()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getUnsettled()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isClosed()
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getName()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Session getSession()
    {
        // TODO Auto-generated method stub
        return null;
    }

}
