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
 * InternalException encapsulates error code 541, or {@link AMQConstant#INTERNAL_ERROR} exceptions relating to the
 * AMQ protocol. It is used to report internal failures and errors that occur within the broker.
 */
public class AMQInternalException extends AMQException
{
    /** serialVersionUID */
    private static final long serialVersionUID = 2544449432843381112L;

    /**
     * Creates an exception with an optional message and optional underlying cause.
     *
     * @param msg The exception message. May be null if not to be set.
     * @param cause The underlying cause of the exception. May be null if not to be set.
     */
    public AMQInternalException(String msg, Throwable cause)
    {
        super(AMQConstant.INTERNAL_ERROR, ((msg == null) ? "Internal error" : msg), cause);
    }
    
    public AMQInternalException(String msg) 
    {
        this(msg, null);
    }
}
