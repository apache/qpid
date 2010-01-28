package org.apache.qpid.transport.network.security.sasl;

import java.nio.ByteBuffer;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.SenderException;
import org.apache.qpid.transport.util.Logger;

public class SASLReceiver extends SASLEncryptor implements Receiver<ByteBuffer> {

    Receiver<ByteBuffer> delegate;
    private byte[] netData;
    private static final Logger log = Logger.get(SASLReceiver.class);
    
    public SASLReceiver(Receiver<ByteBuffer> delegate)
    {
        this.delegate = delegate;
    }
    
    @Override
    public void closed() 
    {
        delegate.closed();
    }

    @Override
    public void exception(Throwable t) 
    {
        delegate.equals(t);
    }

    @Override
    public void received(ByteBuffer buf) 
    {
        if (isSecurityLayerEstablished())
        {
            while (buf.hasRemaining())
            {
                int length = Math.min(buf.remaining(),recvBuffSize);
                buf.get(netData, 0, length);
                try
                {
                    byte[] out = saslClient.unwrap(netData, 0, length);
                    delegate.received(ByteBuffer.wrap(out));
                } 
                catch (SaslException e)
                {
                    throw new SenderException("SASL Sender, Error occurred while encrypting data",e);
                }
            }            
        }
        else
        {
            delegate.received(buf);
        }        
    }
    
    public void securityLayerEstablished()
    {
        netData = new byte[recvBuffSize];
        log.debug("SASL Security Layer Established");
    }

}
