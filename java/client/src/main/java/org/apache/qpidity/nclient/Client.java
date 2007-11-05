package org.apache.qpidity.nclient;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.qpid.url.QpidURL;
import org.apache.qpidity.BrokerDetails;
import org.apache.qpidity.ErrorCode;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.nclient.impl.ClientSession;
import org.apache.qpidity.nclient.impl.ClientSessionDelegate;
import org.apache.qpidity.transport.Channel;
import org.apache.qpidity.transport.Connection;
import org.apache.qpidity.transport.ConnectionClose;
import org.apache.qpidity.transport.ConnectionDelegate;
import org.apache.qpidity.transport.ConnectionEvent;
import org.apache.qpidity.transport.ProtocolHeader;
import org.apache.qpidity.transport.SessionDelegate;
import org.apache.qpidity.transport.network.mina.MinaHandler;


public class Client implements org.apache.qpidity.nclient.Connection
{
    private AtomicInteger _channelNo = new AtomicInteger();
    private Connection _conn;
    private ClosedListener _closedListner;
    private final Lock _lock = new ReentrantLock();

    /**
     *
     * @return returns a new connection to the broker.
     */
    public static org.apache.qpidity.nclient.Connection createConnection()
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
                ErrorCode errorCode = ErrorCode.get(connectionClose.getReplyCode());
                if (_closedListner == null && errorCode != ErrorCode.NO_ERROR)
                {
                    throw new RuntimeException
                        (new QpidException("Server closed the connection: Reason " +
                                           connectionClose.getReplyText(),
                                           errorCode,
                                           null));
                }
                else
                {
                    _closedListner.onClosed(errorCode, connectionClose.getReplyText());
                }
            }
        };

        connectionDelegate.setCondition(_lock,negotiationComplete);
        connectionDelegate.setUsername(username);
        connectionDelegate.setPassword(password);
        connectionDelegate.setVirtualHost(virtualHost);

        _conn = MinaHandler.connect(host, port,connectionDelegate);

        // XXX: hardcoded version numbers
        _conn.send(new ConnectionEvent(0, new ProtocolHeader(1, 0, 10)));

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
    public void connect(QpidURL url) throws QpidException
    {
        // temp impl to tests
        BrokerDetails details = url.getAllBrokerDetails().get(0);
        connect(details.getHost(),
                details.getPort(),
                details.getVirtualHost(),
                details.getUserName(),
                details.getPassword());
    }

    public void close() throws QpidException
    {
        Channel ch = _conn.getChannel(0);
        ch.connectionClose(0, "client is closing", 0, 0);
        _conn.close();
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
         ClientSession clientSession =  (ClientSession) createSession(expiryInSeconds);
         clientSession.dtxDemarcationSelect();
         return (DtxSession) clientSession;
    }

    public void setClosedListener(ClosedListener closedListner)
    {
        _closedListner = closedListner;
    }

}
