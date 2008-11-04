package org.apache.qpid.transport.network.ssl;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;

import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.SenderException;
import org.apache.qpid.transport.util.Logger;

public class SSLSender implements Sender<ByteBuffer>
{
    private Sender<ByteBuffer> delegate;
    private SSLEngine engine;
    private int sslBufSize;
    private ByteBuffer netData;
    
    private final Object engineState = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    private static final Logger log = Logger.get(SSLSender.class);    
    
    public SSLSender(SSLEngine engine, Sender<ByteBuffer> delegate)
    {
        this.engine = engine;
        this.delegate = delegate;        
        sslBufSize = engine.getSession().getPacketBufferSize();
        netData = ByteBuffer.allocate(sslBufSize);      
    }

    public void close()
    {
        if (!closed.getAndSet(true))
        {
            if (engine.isOutboundDone())
            {
                return;
            }
            log.debug("Closing SSL connection");
            engine.closeOutbound();
            send(ByteBuffer.allocate(0));
            flush();  
            while (!engine.isOutboundDone())
            {
                synchronized(engineState)
                {
                    try
                    {
                        engineState.wait();
                    }
                    catch(InterruptedException e)
                    {
                        // pass
                    }
                }
            }
            delegate.close();
        }
    }

    public void flush()
    {
        delegate.flush();        
    }

    public void send(ByteBuffer appData)
    {
        if (closed.get())
        {
            throw new SenderException("SSL Sender is closed");
        }   

        HandshakeStatus handshakeStatus;
        Status status;
        
        while(appData.hasRemaining())
        {        

            int read = 0;
            try
            {
                SSLEngineResult result = engine.wrap(appData, netData);        
                read   = result.bytesProduced();
                status = result.getStatus();
                handshakeStatus = result.getHandshakeStatus();
                
            }
            catch(SSLException e)
            {
                throw new SenderException("SSL, Error occurred while encrypting data",e);
            }            
            
            if(read > 0)
            {
                int limit = netData.limit();
                netData.limit(netData.position());
                netData.position(netData.position() - read);
                
                ByteBuffer data = netData.slice();
                
                netData.limit(limit);
                netData.position(netData.position() + read);
                
                delegate.send(data);
            }
            
            switch(status) 
            {
                case CLOSED:
                    throw new SenderException("SSLEngine is closed");
                
                case BUFFER_OVERFLOW:
                    netData.clear();
                    continue;
                    
                case OK:                        
                    break; // do nothing 
                
                default:
                    throw new IllegalStateException("SSLReceiver: Invalid State " + status);
            }          
            
            switch (handshakeStatus)
            {
                case NEED_WRAP:
                    if (netData.hasRemaining())
                    {
                        continue;
                    }
                
                case NEED_TASK:
                    doTasks();
                    break;
                   
                case NEED_UNWRAP:
                    flush();
                    synchronized(engineState)
                    {
                        try
                        {
                            engineState.wait();
                        }
                        catch(InterruptedException e)
                        {
                            // pass
                        }
                    }
                    break;
                    
                case FINISHED:                     
                case NOT_HANDSHAKING:
                    break; //do  nothing
                      
                default:
                    throw new IllegalStateException("SSLReceiver: Invalid State " + status);
            }
            
        }
    }
    
    public void doTasks() 
    {
        Runnable runnable;
        while ((runnable = engine.getDelegatedTask()) != null) {
            runnable.run();
        }
    }    
    
    public Object getNotificationToken()
    {
        return engineState;
    }
}
