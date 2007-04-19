/* Copyright Rupert Smith, 2005 to 2006, all rights reserved. */
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
package org.apache.qpid.configuration;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;

/**
 * Indicates an error parsing a property expansion.
 */
public class PropertyException extends AMQException
{
    public PropertyException(String message)
    {
        super(message);
    }

    public PropertyException(String msg, Throwable t)
    {
        super(msg, t);
    }

    public PropertyException(AMQConstant errorCode, String msg, Throwable t)
    {
        super(errorCode, msg, t);
    }

    public PropertyException(AMQConstant errorCode, String msg)
    {
        super(errorCode, msg);
    }

    /*public PropertyException(Logger logger, String msg, Throwable t)
    {
        super(logger, msg, t);
    }

    public PropertyException(Logger logger, String msg)
    {
        super(logger, msg);
    }

    public PropertyException(Logger logger, AMQConstant errorCode, String msg)
    {
        super(logger, errorCode, msg);
    }*/
}
