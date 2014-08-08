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

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.protocol.AMQConstant;

/**
 * AMQException forms the root exception of all exceptions relating to the AMQ protocol. It provides space to associate
 * a required AMQ error code with the exception, which is a numeric value, with a meaning defined by the protocol.
 */
public class AMQException extends Exception
{
    /** Holds the AMQ error code constant associated with this exception. */
    private AMQConstant _errorCode;

    private boolean _isHardError;

    /**
     * Creates an exception with an optional error code, optional message and optional underlying cause.
     *
     * @param errorCode The error code. May be null if not to be set.
     * @param msg       The exception message. May be null if not to be set.
     * @param cause         The underlying cause of the exception. May be null if not to be set.
     */
    public AMQException(AMQConstant errorCode, String msg, Throwable cause)
    {
        // isHardError is defaulted to true to avoid unnessacery modification to
        // existing code.
        this(errorCode,true,msg,cause);
    }

    /**
     * Creates an exception with an optional error code, optional message and optional underlying cause.
     *
     * @param errorCode   The error code. May be null if not to be set.
     * @param isHardError Denotes if the underlying error is considered a hard error.
     * @param msg         The exception message. May be null if not to be set.
     * @param cause       The underlying cause of the exception. May be null if not to be set.
     */
    public AMQException(AMQConstant errorCode, boolean isHardError, String msg, Throwable cause)
    {
        super(((msg == null) ? "" : msg), cause);
        _errorCode = errorCode;
        _isHardError = isHardError;
    }

    /*
     * Deprecated constructors brought from M2.1
     */
    @Deprecated
    public AMQException(String msg)
    {
        this(null, (msg == null) ? "" : msg);
    }

    @Deprecated
    public AMQException(AMQConstant errorCode, String msg)
    {
        this(errorCode, (msg == null) ? "" : msg, null);
    }

    @Deprecated
    public AMQException(String msg, Throwable cause)
    {
        this(null, msg, cause);
    }

    @Override
    public String toString()
    {
        return getClass().getName() + ": " + getMessage() + (_errorCode == null ? "" : " [error code " + _errorCode + "]");
    }

    /**
     * Gets the AMQ protocol exception code associated with this exception.
     *
     * @return The AMQ protocol exception code associated with this exception.
     */
    public AMQConstant getErrorCode()
    {
        return _errorCode;
    }

    public boolean isHardError()
    {
        return _isHardError;
    }

    /**
     * Rethrown this exception as a new exception.
     *
     * Attempt to create a new exception of the same class if they have the default constructor of:
     * {AMQConstant.class, String.class, Throwable.class}.
     *
     * @return cloned exception
     */
    public AMQException cloneForCurrentThread()
    {
        Class amqeClass = this.getClass();
        Class<?>[] paramClasses = {AMQConstant.class, String.class, Throwable.class};
        Object[] params = {getErrorCode(), getMessage(), this};

        AMQException newAMQE;

        try
        {
            newAMQE = (AMQException) amqeClass.getConstructor(paramClasses).newInstance(params);
        }
        catch (Exception creationException)
        {
            newAMQE = new AMQException(getErrorCode(), getMessage(), this);
        }

        return newAMQE;
    }

}
