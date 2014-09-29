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
package org.apache.qpid.server.protocol.v0_8.handler;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicRecoverSyncBody;
import org.apache.qpid.framing.BasicRecoverSyncOkBody;
import org.apache.qpid.framing.ChannelAlertBody;
import org.apache.qpid.framing.MethodDispatcher;
import org.apache.qpid.framing.QueueUnbindBody;
import org.apache.qpid.framing.QueueUnbindOkBody;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;

public class ServerMethodDispatcherImpl_8_0
        extends ServerMethodDispatcherImpl
        implements MethodDispatcher
{
    public ServerMethodDispatcherImpl_8_0(AMQProtocolSession<?> connection)
    {
        super(connection);
    }

    public boolean dispatchChannelAlert(ChannelAlertBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    @Override
    public boolean dispatchQueueUnbindOk(final QueueUnbindOkBody queueUnbindOkBody, final int channelId)
    {
        return false;
    }

    @Override
    public boolean dispatchBasicRecoverSyncOk(final BasicRecoverSyncOkBody body,
                                              final int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    @Override
    public boolean dispatchQueueUnbind(final QueueUnbindBody queueUnbindBody, final int channelId) throws AMQException
    {
        return false;
    }

    @Override
    public boolean dispatchBasicRecoverSync(final BasicRecoverSyncBody body, final int channelId)
            throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }
}
