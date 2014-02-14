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
package org.apache.qpid.server.protocol;

import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.stats.StatisticsGatherer;

import java.util.List;
import java.util.UUID;

public interface AMQConnectionModel extends StatisticsGatherer
{
    /**
     * Close the underlying Connection
     *
     * @param cause
     * @param message
     */
    public void close(AMQConstant cause, String message);

    public void block();

    public void unblock();

    /**
     * Close the given requested Session
     *
     * @param session
     * @param cause
     * @param message
     */
    public void closeSession(AMQSessionModel session, AMQConstant cause, String message);

    public long getConnectionId();

    /**
     * Get a list of all sessions using this connection.
     *
     * @return a list of {@link AMQSessionModel}s
     */
    public List<AMQSessionModel> getSessionModels();

    /**
     * Return a {@link LogSubject} for the connection.
     */
    public LogSubject getLogSubject();

    public String getUserName();

    public boolean isSessionNameUnique(byte[] name);

    String getRemoteAddressString();

    String getClientId();

    String getClientVersion();

    String getClientProduct();

    String getPrincipalAsString();

    long getSessionCountLimit();

    long getLastIoTime();

    Port getPort();

    Transport getTransport();

    void stop();

    boolean isStopped();

    String getVirtualHostName();
}
