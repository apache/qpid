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
 * If the communication layer detects a serious problem with a <CODE>connection</CODE>, it
 * informs the connection's ExceptionListener
 */
public interface ExceptionListener
{
    /**
     * If the communication layer detects a serious problem with a connection, it
     * informs the connection's ExceptionListener
     *
     * @param exception The exception comming from the communication layer.
     * @see Connection
     */
    public void onException(QpidException exception);
}