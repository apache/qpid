/*
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
 */
package org.apache.qpidity.nclient;

import org.apache.qpidity.QpidException;

/**
 * This represents a physical connection to a broker.
 */
public interface Connection
{
   /**
    * Establish the connection using the given parameters
    *
    * @param host  host name
    * @param port  port number
    * @param virtualHost the virtual host name
    * @param username user name
    * @param password password
    * @throws QpidException If the communication layer fails to establish the connection.
    */
   public void connect(String host, int port,String virtualHost,String username, String password) throws QpidException;

   /**
    * Establish the connection with the broker identified by the URL.
    *
    * @param url Specifies the URL of the broker.
    * @throws QpidException If the communication layer fails to connect with the broker, an exception is thrown.
    */
   public void connect(String url) throws QpidException;

    /**
     * Close this connection.
     *
     * @throws QpidException if the communication layer fails to close the connection.
     */
    public void close() throws QpidException;

    /**
     * Create a session for this connection.
     * <p> The returned session is suspended
     * (i.e. this session is not attached to an underlying channel)
     *
     * @param expiryInSeconds Expiry time expressed in seconds, if the value is less than
     * or equal to 0 then the session does not expire.
     * @return A newly created (suspended) session.
     */
    public Session createSession(long expiryInSeconds);

    /**
     * Create a DtxSession for this connection.
     * <p> A Dtx Session must be used when resources have to be manipulated as
     * part of a global transaction.
     * <p> The retuned DtxSession is suspended
     * (i.e. this session is not attached with an underlying channel)
     *
     * @param expiryInSeconds Expiry time expressed in seconds, if the value is less than or equal
     * to 0 then the session does not expire.
     * @return A newly created (suspended) DtxSession.
     */
    public DtxSession createDTXSession(int expiryInSeconds);

    /**
     * If the communication layer detects a serious problem with a connection, it
     * informs the connection's ClosedListener
     *
     * @param exceptionListner The ClosedListener
     */
    public void setClosedListener(ClosedListener exceptionListner);
}
