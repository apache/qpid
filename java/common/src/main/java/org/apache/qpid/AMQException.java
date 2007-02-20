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

import org.apache.log4j.Logger;
import org.apache.qpid.protocol.AMQConstant;

/** Generic AMQ exception. */
public class AMQException extends Exception
{
    private AMQConstant _errorCode;

    public AMQException(String message)
    {
        super(message);
        //fixme This method needs removed and all AMQExceptions need a valid error code
        _errorCode = AMQConstant.getConstant(-1);
    }

    public AMQException(String msg, Throwable t)
    {
        super(msg, t);
        //fixme This method needs removed and all AMQExceptions need a valid error code
        _errorCode = AMQConstant.getConstant(-1);
    }

    public AMQException(AMQConstant errorCode, String msg, Throwable t)
    {
        super(msg + " [error code " + errorCode + ']', t);
        _errorCode = errorCode;
    }

    public AMQException(AMQConstant errorCode, String msg)
    {
        super(msg + " [error code " + errorCode + ']');
        _errorCode = errorCode;
    }

    public AMQException(Logger logger, String msg, Throwable t)
    {
        this(msg, t);
        logger.error(getMessage(), this);
    }

    public AMQException(Logger logger, String msg)
    {
        this(msg);
        logger.error(getMessage(), this);
    }

    public AMQException(Logger logger, AMQConstant errorCode, String msg)
    {
        this(errorCode, msg);
        logger.error(getMessage(), this);
    }

    public AMQConstant getErrorCode()
    {
        return _errorCode;
    }

}
