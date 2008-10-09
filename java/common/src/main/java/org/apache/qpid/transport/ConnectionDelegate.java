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

import org.apache.qpid.transport.util.Logger;

import static org.apache.qpid.transport.Connection.State.*;


/**
 * ConnectionDelegate
 *
 * @author Rafael H. Schloming
 */

/**
 * Currently only implemented client specific methods
 * the server specific methods are dummy impls for testing
 *
 * the connectionClose is kind of different for both sides
 */
public abstract class ConnectionDelegate extends MethodDelegate<Channel>
{

    private static final Logger log = Logger.get(ConnectionDelegate.class);

    public SessionDelegate getSessionDelegate()
    {
        return new SessionDelegate();
    }

    public abstract void init(Channel ch, ProtocolHeader hdr);

    @Override public void connectionClose(Channel ch, ConnectionClose close)
    {
        Connection conn = ch.getConnection();
        ch.connectionCloseOk();
        conn.getSender().close();
        conn.closeCode(close);
        conn.setState(CLOSE_RCVD);
    }

    @Override public void connectionCloseOk(Channel ch, ConnectionCloseOk ok)
    {
        Connection conn = ch.getConnection();
        conn.getSender().close();
    }

}
