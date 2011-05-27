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

import org.apache.qpid.AMQException;
import org.apache.qpid.server.logging.LogSubject;

public interface AMQSessionModel
{
    public Object getID();

    public AMQConnectionModel getConnectionModel();

    public String getClientID();
    
    public void close() throws AMQException;

    public LogSubject getLogSubject();
    
    /**
     * This method is called from the housekeeping thread to check the status of
     * transactions on this session and react appropriately.
     * 
     * If a transaction is open for too long or idle for too long then a warning
     * is logged or the connection is closed, depending on the configuration. An open
     * transaction is one that has recent activity. The transaction age is counted
     * from the time the transaction was started. An idle transaction is one that 
     * has had no activity, such as publishing or acknowledgeing messages.
     * 
     * @param openWarn time in milliseconds before alerting on open transaction
     * @param openClose time in milliseconds before closing connection with open transaction
     * @param idleWarn time in milliseconds before alerting on idle transaction
     * @param idleClose time in milliseconds before closing connection with idle transaction
     */
    public void checkTransactionStatus(long openWarn, long openClose, long idleWarn, long idleClose) throws AMQException;
}
