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

import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.protocol.AMQConstant;

/**
 * AMQChannelException indicates that an error that requires the channel to be closed has occurred.
 */
public class AMQChannelException extends AMQException
{
    private final int _classId;
    private final int _methodId;
    /* AMQP version for which exception ocurred */
    private final MethodRegistry _methodRegistry;


    public AMQChannelException(AMQConstant errorCode,
                               String msg,
                               int classId,
                               int methodId,
                               MethodRegistry methodRegistry)
    {
        super(errorCode, msg);
        _classId = classId;
        _methodId = methodId;
        _methodRegistry = methodRegistry;

    }

    public int getClassId()
    {
        return _classId;
    }

    public int getMethodId()
    {
        return _methodId;
    }

    public MethodRegistry getMethodRegistry()
    {
        return _methodRegistry;
    }

}
