package org.apache.qpid.transport.network.security.sasl;
/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */


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
    
    public void closed() 
    {
        delegate.closed();
    }


    public void exception(Throwable t) 
    {
        delegate.exception(t);
    }

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
