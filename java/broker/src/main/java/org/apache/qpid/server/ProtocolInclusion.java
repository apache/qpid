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
package org.apache.qpid.server;

import java.util.HashMap;
import java.util.Map;

public enum ProtocolInclusion
{
    v0_8("include-0-8","--include-0-8"),
    v0_9("include-0-9", "--include-0-9"),
    v0_9_1("include-0-9-1", "--include-0-9-1"),
    v0_10("include-0-10", "--include-0-10"),
    v1_0("include-1-0", "--include-1-0");

    private static final Map<String, ProtocolInclusion> MAP = new HashMap<String,ProtocolInclusion>();

    static
    {
        for(ProtocolInclusion pe : ProtocolInclusion.values())
        {
            MAP.put(pe.getArg(), pe);
        }
    }

    private String _arg;
    private String _includeName;

    private ProtocolInclusion(final String includeName, final String arg)
    {
        _includeName = includeName;
        _arg = arg;
    }

    public String getArg()
    {
        return _arg;
    }

    public String getIncludeName()
    {
        return _includeName;
    }

    public static ProtocolInclusion lookup(final String arg)
    {
        ProtocolInclusion ex = MAP.get(arg);

        if(ex == null)
        {
            throw new IllegalArgumentException(arg + " is not a valid protocol inclusion");
        }

        return ex;
    }
}
