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
package org.apache.qpid.protocol;

import java.util.Map;
import java.util.HashMap;

public final class AMQConstant
{
    private int _code;

    private String _name;

    private static Map _codeMap = new HashMap();

    private AMQConstant(int code, String name, boolean map)
    {
        _code = code;
        _name = name;
        if (map)
        {
            _codeMap.put(new Integer(code), this);
        }
    }

    public String toString()
    {
        return _code + ": " + _name;
    }

    public int getCode()
    {
        return _code;
    }

    public String getName()
    {
        return _name;
    }
    
    public static final AMQConstant FRAME_MIN_SIZE = new AMQConstant(4096, "frame min size", true);

    public static final AMQConstant FRAME_END = new AMQConstant(206, "frame end", true);

    public static final AMQConstant REPLY_SUCCESS = new AMQConstant(200, "reply success", true);

    public static final AMQConstant NOT_DELIVERED = new AMQConstant(310, "not delivered", true);

    public static final AMQConstant MESSAGE_TOO_LARGE = new AMQConstant(311, "message too large", true);

    public static final AMQConstant NO_ROUTE = new AMQConstant(312, "no route", true);
    
    public static final AMQConstant NO_CONSUMERS = new AMQConstant(313, "no consumers", true);

    public static final AMQConstant CONTEXT_IN_USE = new AMQConstant(320, "context in use", true);

    public static final AMQConstant CONTEXT_UNKNOWN = new AMQConstant(321, "context unknown", true);

    public static final AMQConstant INVALID_PATH = new AMQConstant(402, "invalid path", true);

    public static final AMQConstant ACCESS_REFUSED = new AMQConstant(403, "access refused", true);

    public static final AMQConstant NOT_FOUND = new AMQConstant(404, "not found", true);

    public static final AMQConstant FRAME_ERROR = new AMQConstant(501, "frame error", true);

    public static final AMQConstant SYNTAX_ERROR = new AMQConstant(502, "syntax error", true);

    public static final AMQConstant COMMAND_INVALID = new AMQConstant(503, "command invalid", true);

    public static final AMQConstant CHANNEL_ERROR = new AMQConstant(504, "channel error", true);

    public static final AMQConstant RESOURCE_ERROR = new AMQConstant(506, "resource error", true);

    public static final AMQConstant NOT_ALLOWED = new AMQConstant(507, "not allowed", true);

    public static final AMQConstant NOT_IMPLEMENTED = new AMQConstant(540, "not implemented", true);

    public static final AMQConstant INTERNAL_ERROR = new AMQConstant(541, "internal error", true);

    public static AMQConstant getConstant(int code)
    {
        AMQConstant c = (AMQConstant) _codeMap.get(new Integer(code));
        if (c == null)
        {
            c = new AMQConstant(code, "unknown code", false);
        }
        return c;
    }
}
