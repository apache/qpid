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

package org.apache.qpid.qmf;

import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.transport.codec.BBEncoder;

public class QMFBrokerResponseCommand extends QMFCommand
{
    private QMFCommandHeader _header;
    private VirtualHost _virtualHost;

    public QMFBrokerResponseCommand(QMFBrokerRequestCommand qmfBrokerRequestCommand, VirtualHost virtualHost)
    {
        super( new QMFCommandHeader(qmfBrokerRequestCommand.getHeader().getVersion(),
                                    qmfBrokerRequestCommand.getHeader().getSeq(),
                                    QMFOperation.BROKER_RESPONSE));
        _virtualHost = virtualHost;
    }

    public void encode(BBEncoder encoder)
    {
        super.encode(encoder);
        encoder.writeUuid(_virtualHost.getBrokerId());
    }
}
