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

import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.protocol.AMQConstant;

/**
 * AMQConnectionException indicates that an error that requires the channel to be closed has occurred.
 */
public class AMQConnectionException extends AMQException
{
    private final int _classId;
    private final int _methodId;

    private final MethodRegistry _methodRegistry;

    public AMQConnectionException(AMQConstant errorCode, String msg, AMQMethodBody body, MethodRegistry methodRegistry)
    {
        this(errorCode, msg, body.getClazz(), body.getMethod(), methodRegistry, null);
    }

    public AMQConnectionException(AMQConstant errorCode, String msg, int classId, int methodId, MethodRegistry methodRegistry,
                                  Throwable cause)
    {
        super(errorCode, msg, cause);
        _classId = classId;
        _methodId = methodId;
        _methodRegistry = methodRegistry;

    }

    public AMQFrame getCloseFrame()
    {
        return new AMQFrame(0,
                            _methodRegistry.createConnectionCloseBody(getErrorCode().getCode(),
                                                                      AMQShortString.validValueOf(getMessage()),
                                                                      _classId,
                                                                      _methodId));

    }

}
