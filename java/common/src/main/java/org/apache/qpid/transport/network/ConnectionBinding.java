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
package org.apache.qpid.transport.network;

import java.nio.ByteBuffer;

import org.apache.qpid.transport.Binding;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ConnectionDelegate;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.Sender;

/**
 * ConnectionBinding
 *
 */

public class ConnectionBinding implements Binding<Connection,ByteBuffer>
{

    private static final int MAX_FRAME_SIZE = 64 * 1024 - 1;

    private final ConnectionDelegate delegate;

    public ConnectionBinding(ConnectionDelegate delegate)
    {
        this.delegate = delegate;
    }

    public Connection endpoint(Sender<ByteBuffer> sender)
    {
        // XXX: hardcoded max-frame
        return new Connection
            (new Disassembler(sender, MAX_FRAME_SIZE), delegate);
    }

    public Receiver<ByteBuffer> receiver(Connection conn)
    {
        return new InputHandler(new Assembler(conn));
    }

}
