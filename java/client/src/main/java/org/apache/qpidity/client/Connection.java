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
package org.apache.qpidity.client;


import java.net.URL;

import org.apache.qpidity.QpidException;

/**
 * This represents a physical connection to a broker.
 */
public interface Connection
{
   /**
    * Establish the connection using the given parameters
    * 
    * @param host
    * @param port
    * @param username
    * @param password
    * @throws QpidException
    */ 
   public void connect(String host, int port,String virtualHost,String username, String password) throws QpidException;
    
    /**
     * Establish the connection with the broker identified by the provided URL.
     *
     * @param url The URL of the broker.
     * @throws QpidException If the communication layer fails to connect with the broker.
     */
    public void connect(URL url) throws QpidException;

    /**
     * Close this connection.
     *
     * @throws QpidException if the communication layer fails to close the connection.
     */
    public void close() throws QpidException;


    /**
     * Create a session for this connection.
     * <p> The retuned session is suspended
     * (i.e. this session is not attached with an underlying channel)
     *
     * @param expiryInSeconds Expiry time expressed in seconds, if the value is <= 0 then the session does not expire.
     * @return A Newly created (suspended) session.
     */
    public Session createSession(long expiryInSeconds);

    /**
     * Create a DtxSession for this connection.
     * <p> A Dtx Session must be used when resources have to be manipulated as
     * part of a global transaction.
     * <p> The retuned DtxSession is suspended
     * (i.e. this session is not attached with an underlying channel)
     *
     * @param expiryInSeconds Expiry time expressed in seconds, if the value is <= 0 then the session does not expire.
     * @return A Newly created (suspended) DtxSession.
     */
    public DtxSession createDTXSession(int expiryInSeconds);

    /**
     * If the communication layer detects a serious problem with a connection, it
     * informs the connection's ExceptionListener
     *
     * @param exceptionListner The execptionListener
     */
    public void setExceptionListener(ExceptionListener exceptionListner);
}
