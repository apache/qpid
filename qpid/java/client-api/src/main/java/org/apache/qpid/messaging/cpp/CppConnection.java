package org.apache.qpid.messaging.cpp;

import org.apache.qpid.messaging.Connection;
import org.apache.qpid.messaging.Session;

public class CppConnection implements Connection
{
    private org.apache.qpid.messaging.cpp.jni.Connection _cppConn;
    
    public CppConnection(String url)
    {
        _cppConn = new org.apache.qpid.messaging.cpp.jni.Connection(url);
    }

    @Override
    public void open()
    {
        _cppConn.open();
    }

    @Override
    public boolean isOpen()
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void close()
    {
        _cppConn.close();
    }

    @Override
    public Session createSession(String name)
    {
        return new CppSession(_cppConn.createSession());
    }

    @Override
    public Session createTransactionalSession(String name)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getAuthenticatedUsername()
    {
        // TODO Auto-generated method stub
        return null;
    }

}
