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
 * AMQException forms the root exception of all exceptions relating to the AMQ protocol. It provides space to associate
 * a required AMQ error code with the exception, which is a numeric value, with a meaning defined by the protocol.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represents an exception condition associated with an AMQ protocol status code.
 * </table>
 *
 * @todo This exception class is also used as a generic exception throughout Qpid code. This usage may not be strictly
 *       correct if this is to signify a protocol exception. Should review.
 */
public class AMQException extends Exception
{
    /** Holds the AMQ error code constant associated with this exception. */
    private AMQConstant _errorCode;

    /**
     * Creates an exception with an optional error code, optional message and optional underlying cause.
     *
     * @param errorCode The error code. May be null if not to be set.
     * @param msg       The exception message. May be null if not to be set.
     * @param t         The underlying cause of the exception. May be null if not to be set.
     */
    public AMQException(AMQConstant errorCode, String msg, Throwable t)
    {
        super(((msg == null) ? "" : msg) + ((errorCode == null) ? "" : (" [error code " + errorCode + "]")), t);
        _errorCode = errorCode;
    }

    /**
     * @param message
     *
     * @deprecated Use {@link #AMQException(org.apache.qpid.protocol.AMQConstant, String, Throwable)} instead.
     */
    public AMQException(String message)
    {
        super(message);
        // fixme This method needs removed and all AMQExceptions need a valid error code
        _errorCode = AMQConstant.getConstant(-1);
    }

    /**
     * @param msg
     * @param t
     *
     * @deprecated Use {@link #AMQException(org.apache.qpid.protocol.AMQConstant, String, Throwable)} instead.
     */
    public AMQException(String msg, Throwable t)
    {
        super(msg, t);
        // fixme This method needs removed and all AMQExceptions need a valid error code
        _errorCode = AMQConstant.getConstant(-1);
    }

    /**
     * @param errorCode
     * @param msg
     *
     * @deprecated Use {@link #AMQException(org.apache.qpid.protocol.AMQConstant, String, Throwable)} instead.
     */
    public AMQException(AMQConstant errorCode, String msg)
    {
        super(msg + " [error code " + errorCode + ']');
        _errorCode = errorCode;
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
}
