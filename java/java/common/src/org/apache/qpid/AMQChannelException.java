/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid;

import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.AMQFrame;

public class AMQChannelException extends AMQException
{
    private final int _classId;
    private final int _methodId;

    public AMQChannelException(int errorCode, String msg, int classId, int methodId, Throwable t)
    {
        super(errorCode, msg, t);
        _classId = classId;
        _methodId = methodId;
    }

    public AMQChannelException(int errorCode, String msg, int classId, int methodId)
    {
        super(errorCode, msg);
        _classId = classId;
        _methodId = methodId;
    }

    public AMQFrame getCloseFrame(int channel)
    {
        return ChannelCloseBody.createAMQFrame(channel, getErrorCode(), getMessage(), _classId, _methodId);
    }
}
