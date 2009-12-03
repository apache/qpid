/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.logging.actors;

import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.subjects.ChannelLogSubject;
import org.apache.qpid.server.protocol.AMQProtocolSession;

import java.text.MessageFormat;

/**
 * An AMQPChannelActor represtents a connection through the AMQP port with an
 * associated Channel.
 *
 * <p/>
 * This is responsible for correctly formatting the LogActor String in the log
 * <p/>
 * [con:1(user@127.0.0.1/)/ch:1]
 * <p/>
 * To do this it requires access to the IO Layers as well as a Channel
 */
public class AMQPChannelActor extends AbstractActor
{
    private final String _logString;

    /**
     * Create a new ChannelActor
     *
     * @param channel    The Channel for this LogActor
     * @param rootLogger The root Logger that this LogActor should use
     */
    public AMQPChannelActor(AMQChannel channel, RootMessageLogger rootLogger)
    {
        super(rootLogger);

        AMQProtocolSession session = channel.getProtocolSession();

        /**
         * LOG FORMAT used by the AMQPConnectorActor follows
         * ChannelLogSubject.CHANNEL_FORMAT :
         * con:{0}({1}@{2}/{3})/ch:{4}
         *
         * Uses a MessageFormat call to insert the requried values according to
         * these indicies:
         *
         * 0 - Connection ID
         * 1 - User ID
         * 2 - IP
         * 3 - Virtualhost
         */
        _logString = "[" + MessageFormat.format(ChannelLogSubject.CHANNEL_FORMAT,
                                               session.getSessionID(),
                                               session.getPrincipal().getName(),
                                               session.getRemoteAddress(),
                                               session.getVirtualHost().getName(),
                                               channel.getChannelId())
                    + "] ";
    }

    public String getLogMessage()
    {
        return _logString;
    }
}

