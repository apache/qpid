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
package org.apache.qpid.client;

import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;

/**
 * Authentication Exception represents all failures to authenticate access to a broker.
 * 
 * This exception encapsulates error code 530, or {@link AMQConstant#NOT_ALLOWED}
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represent failure to authenticate the client.
 * </table>
 */
public class AMQAuthenticationException extends AMQException
{
    /** serialVersionUID */
    private static final long serialVersionUID = 6045925435200184200L;

    /**
     * Creates an exception with an optional message and optional underlying cause.
     *
     * @param msg The exception message. May be null if not to be set.
     * @param cause The underlying cause of the exception. May be null if not to be set.
     */
    public AMQAuthenticationException(String msg, Throwable cause)
    {
        super(AMQConstant.NOT_ALLOWED, ((msg == null) ? "Authentication error" : msg), cause);
    }
    
    public AMQAuthenticationException(String msg) 
    {
        this(msg, null);
    }
    
    public AMQAuthenticationException()
    {
        this(null);
    }
}
