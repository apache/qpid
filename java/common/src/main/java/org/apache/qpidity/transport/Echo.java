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
package org.apache.qpidity.transport;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.qpidity.transport.network.mina.MinaHandler;


/**
 * Echo
 *
 */

public class Echo extends SessionDelegate
{

    private MessageTransfer xfr = null;

    public void messageTransfer(Session ssn, MessageTransfer xfr)
    {
        this.xfr = xfr;
        ssn.invoke(xfr);
    }

    public void header(Session ssn, Header hdr)
    {
        ssn.header(hdr);
    }

    public void data(Session ssn, Data data)
    {
        ssn.data(data.getData());
        if (data.isLast())
        {
            ssn.endData();
        }

        // XXX: should be able to get command-id from any segment
        ssn.processed(xfr);
    }

    public static final void main(String[] args) throws IOException
    {
        ConnectionDelegate delegate = new ConnectionDelegate()
        {
            public SessionDelegate getSessionDelegate()
            {
                return new Echo();
            }
            public void exception(Throwable t)
            {
                t.printStackTrace();
            }
            public void closed() {}
        };

        //hack
        delegate.setUsername("guest");
        delegate.setPassword("guest");

        MinaHandler.accept("0.0.0.0", 5672, delegate);
    }

}
