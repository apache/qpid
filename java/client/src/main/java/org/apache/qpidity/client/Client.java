package org.apache.qpidity.client;

import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.qpidity.Channel;
import org.apache.qpidity.Connection;
import org.apache.qpidity.ConnectionClose;
import org.apache.qpidity.ConnectionDelegate;
import org.apache.qpidity.ErrorCode;
import org.apache.qpidity.MinaHandler;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.SessionDelegate;


public class Client implements org.apache.qpidity.client.Connection
{
    private AtomicInteger _channelNo = new AtomicInteger();
    private Connection _conn;
    private ExceptionListener _exceptionListner;
    private final Lock _lock = new ReentrantLock();
    
    public static org.apache.qpidity.client.Connection createConnection()
    {
        return new Client();
    }
    
    public void connect(String host, int port,String virtualHost,String username, String password) throws QpidException
    {
        Condition negotiationComplete = _lock.newCondition();
        _lock.lock();
        
        ConnectionDelegate connectionDelegate = new ConnectionDelegate()
        {            
            public SessionDelegate getSessionDelegate()
            {
                return new ClientSessionDelegate();
            }
            
            @Override public void connectionClose(Channel context, ConnectionClose connectionClose) 
            {
                _exceptionListner.onException(
                        new QpidException("Server closed the connection: Reason " + 
                                           connectionClose.getReplyText(),
                                           ErrorCode.get(connectionClose.getReplyCode()),
                                           null));
            }
        };
        
        connectionDelegate.setCondition(_lock,negotiationComplete);
        connectionDelegate.setUsername(username);
        connectionDelegate.setPassword(password);
        connectionDelegate.setVirtualHost(virtualHost);
        
        _conn = MinaHandler.connect(host, port,connectionDelegate);
                
        _conn.getOutputHandler().handle(_conn.getHeader().toByteBuffer());        
        
        try
        {
            negotiationComplete.await();
        }
        catch (Exception e)
        {
            //
        }
        finally
        {
            _lock.unlock();
        }
    }
    
    /*
     * Until the dust settles with the URL disucssion
     * I am not going to implement this.
     */
    public void connect(URL url) throws QpidException
    {
        throw new UnsupportedOperationException(); 
    }
    
    public void close() throws QpidException
    {   
        Channel ch = _conn.getChannel(0);
        ch.connectionClose(0, "client is closing", 0, 0);
        //need to close the connection underneath as well
    }

    public Session createSession(long expiryInSeconds)
    {
        Channel ch = _conn.getChannel(_channelNo.incrementAndGet());   
        ClientSession ssn = new ClientSession();
        ssn.attach(ch);
        ssn.sessionOpen(expiryInSeconds);
        
        return ssn;
    }

    public DtxSession createDTXSession(int expiryInSeconds)
    {
        // TODO Auto-generated method stub
        return null;
    }
    
    public void setExceptionListener(ExceptionListener exceptionListner)
    {
        _exceptionListner = exceptionListner;        
    }

}
