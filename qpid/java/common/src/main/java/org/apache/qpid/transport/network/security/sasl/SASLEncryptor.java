package org.apache.qpid.transport.network.security.sasl;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;

import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ConnectionException;
import org.apache.qpid.transport.ConnectionListener;

public abstract class SASLEncryptor implements ConnectionListener
{
    protected SaslClient saslClient;
    protected boolean securityLayerEstablished = false;
    protected int sendBuffSize;
    protected int recvBuffSize;

    public boolean isSecurityLayerEstablished()
    {
        return securityLayerEstablished;
    }
    
    public void opened(Connection conn) 
    {
        if (conn.getSaslClient() != null)
        {
            saslClient = conn.getSaslClient();
            if (saslClient.isComplete() && saslClient.getNegotiatedProperty(Sasl.QOP) == "auth-conf")
            {                
                sendBuffSize = Integer.parseInt(
                        (String)saslClient.getNegotiatedProperty(Sasl.RAW_SEND_SIZE));
                recvBuffSize = Integer.parseInt(
                        (String)saslClient.getNegotiatedProperty(Sasl.MAX_BUFFER));
                securityLayerEstablished();
                securityLayerEstablished = true;
            }
        }
    }
    
    public void exception(Connection conn, ConnectionException exception){}
    public void closed(Connection conn) {}
    
    public abstract void securityLayerEstablished();
}
