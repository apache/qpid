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
package org.apache.qpid.transport.network.security.sasl;


import java.util.List;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;

import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ConnectionException;
import org.apache.qpid.transport.ConnectionListener;

public abstract class SASLEncryptor implements ConnectionListener
{
    private SaslClient saslClient;
    private boolean securityLayerEstablished = false;
    private int sendBuffSize;
    private int recvBuffSize;

    public boolean isSecurityLayerEstablished()
    {
        return securityLayerEstablished;
    }
    
    public void opened(Connection conn) 
    {
        if (conn.getSaslClient() != null)
        {
            saslClient = conn.getSaslClient();
            if (saslClient.isComplete() && "auth-conf".equals(saslClient.getNegotiatedProperty(Sasl.QOP)))
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

    @Override
    public boolean redirect(final String host, final List<Object> knownHosts)
    {
        return false;
    }

    public void exception(Connection conn, ConnectionException exception){}
    public void closed(Connection conn) {}
    
    public abstract void securityLayerEstablished();

    public SaslClient getSaslClient()
    {
        return saslClient;
    }

    public int getSendBuffSize()
    {
        return sendBuffSize;
    }

    public int getRecvBuffSize()
    {
        return recvBuffSize;
    }
}
