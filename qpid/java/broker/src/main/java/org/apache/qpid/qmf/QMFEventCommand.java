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

public abstract class QMFEventCommand<T extends QMFEventClass> extends QMFCommand
{
    private final long _timestamp;

    protected QMFEventCommand()
    {
        super(new QMFCommandHeader((byte)'2',0, QMFOperation.EVENT));
        _timestamp = System.currentTimeMillis();
    }

    abstract public T getEventClass();

    @Override
    public void encode(final BBEncoder encoder)
    {
        super.encode(encoder);
        encoder.writeStr8(getEventClass().getPackage().getName());
        encoder.writeStr8(getEventClass().getName());
        encoder.writeBin128(new byte[16]);
        encoder.writeUint64(_timestamp * 1000000L);
        encoder.writeUint8((short) getEventClass().getSeverity().ordinal());
    }
}
