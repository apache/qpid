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

import java.sql.SQLException;

/**
 * StoreException is a specific type of internal error relating to errors in the message store, such as {@link SQLException}.
 */
public class AMQStoreException extends AMQInternalException
{
    /** serialVersionUID */
    private static final long serialVersionUID = 2859681947490637496L;

    /**
     * Creates an exception with an optional message and optional underlying cause.
     *
     * @param msg The exception message. May be null if not to be set.
     * @param cause The underlying cause of the exception. May be null if not to be set.
     */
    public AMQStoreException(String msg, Throwable cause)
    {
        super(((msg == null) ? "Store error" : msg), cause);
    }
    
    public AMQStoreException(String msg) 
    {
        this(msg, null);
    }
}
