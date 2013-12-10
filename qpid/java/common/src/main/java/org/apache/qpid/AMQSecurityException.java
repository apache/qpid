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
package org.apache.qpid;

import org.apache.qpid.protocol.AMQConstant;

/**
 * SecurityException encapsulates error code 403, or {@link AMQConstant#ACCESS_REFUSED} exceptions relating to the
 * AMQ protocol. It is used to report authorisation failures and security errors.
 */
public class AMQSecurityException extends AMQException
{
    /** serialVersionUID */
    private static final long serialVersionUID = 8862069852716968394L;

    /**
     * Creates an exception with an optional message and optional underlying cause.
     *
     * @param msg The exception message. May be null if not to be set.
     * @param cause The underlying cause of the exception. May be null if not to be set.
     */
    public AMQSecurityException(String msg, Throwable cause)
    {
        super(AMQConstant.ACCESS_REFUSED, ((msg == null) ? "Permission denied" : msg), cause);
    }
    
    public AMQSecurityException(String msg) 
    {
        this(msg, null);
    }
    
    public AMQSecurityException()
    {
        this(null);
    }
}
