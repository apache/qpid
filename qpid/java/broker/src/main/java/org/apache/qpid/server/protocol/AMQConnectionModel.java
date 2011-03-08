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

import java.util.List;
import java.util.UUID;

import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.stats.StatisticsGatherer;

public interface AMQConnectionModel extends StatisticsGatherer
{
    /**
     * get a unique id for this connection.
     * 
     * @return a {@link UUID} representing the connection
     */
    public UUID getId();
    
    /**
     * Close the underlying Connection
     * 
     * @param cause
     * @param message
     * @throws org.apache.qpid.AMQException
     */
    public void close(AMQConstant cause, String message) throws AMQException;

    /**
     * Close the given requested Session
     * 
     * @param session
     * @param cause
     * @param message
     * @throws org.apache.qpid.AMQException
     */
    public void closeSession(AMQSessionModel session, AMQConstant cause, String message) throws AMQException;

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
}
