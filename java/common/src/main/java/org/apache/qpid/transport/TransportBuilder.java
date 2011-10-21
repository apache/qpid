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
package org.apache.qpid.transport;

import java.nio.ByteBuffer;

import org.apache.qpid.transport.network.Assembler;
import org.apache.qpid.transport.network.Disassembler;
import org.apache.qpid.transport.network.InputHandler;
import org.apache.qpid.transport.network.NetworkTransport;
import org.apache.qpid.transport.network.Transport;
import org.apache.qpid.transport.network.security.SecurityLayer;

public class TransportBuilder
{
    private Connection con;
    private ConnectionSettings settings;
    private NetworkTransport transport;
    private SecurityLayer securityLayer = new SecurityLayer();
    
    public void init(Connection con) throws TransportException
    {
        this.con = con;
        this.settings = con.getConnectionSettings();
        transport = Transport.getTransport();    
        transport.init(settings);        
        securityLayer.init(con);
    }

    public Sender<ProtocolEvent> buildSenderPipe()
    {
        ConnectionSettings settings = con.getConnectionSettings();
        
        // Io layer
        Sender<ByteBuffer> sender = transport.sender();
        
        // Security layer 
        sender = securityLayer.sender(sender);
        
        Disassembler dis = new Disassembler(sender, settings.getMaxFrameSize());
        return dis;
    }
    
    public void buildReceiverPipe(Receiver<ProtocolEvent> delegate)
    {
        Receiver<ByteBuffer> receiver = new InputHandler(new Assembler(delegate));
        
        // Security layer 
        receiver = securityLayer.receiver(receiver);
        
        //Io layer
        transport.receiver(receiver);        
    } 
    
    public SecurityLayer getSecurityLayer()
    {
        return securityLayer;
    }

}