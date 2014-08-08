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

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.Principal;
import org.apache.qpid.transport.Sender;

public interface NetworkConnection
{
    Sender<ByteBuffer> getSender();

    void start();

    void close();

    /**
     * @return the remote address of the underlying socket.
     */
    SocketAddress getRemoteAddress();

    /**
     * @return the local address of the underlying socket.
     */
    SocketAddress getLocalAddress();

    void setMaxWriteIdle(int sec);

    void setMaxReadIdle(int sec);

    Principal getPeerPrincipal();

    int getMaxReadIdle();

    int getMaxWriteIdle();
}
