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

import java.util.Collection;

/**
 * AMQConnectionFailureException indicates that a connection to a broker could not be formed.
 */
public class AMQConnectionFailureException extends AMQException
{
    private Collection<Exception> _exceptions;
    
	public AMQConnectionFailureException(String message, Throwable cause)
	{
		super(cause instanceof AMQException ? ((AMQException) cause).getErrorCode() : null, message, cause);
	}

    public AMQConnectionFailureException(AMQConstant errorCode, String message, Throwable cause)
    {
        super(errorCode, message, cause);
    }

    public AMQConnectionFailureException(String message, Collection<Exception> exceptions)
    {
        // Blah, I hate ? but java won't let super() be anything other than the first thing, sorry...
        super (null, message, exceptions.isEmpty() ? null : exceptions.iterator().next());
        this._exceptions = exceptions;
    }
    
    public Collection<Exception> getLinkedExceptions()
    {
        return _exceptions;
    }
}
