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

public enum ProtocolExclusion
{
    v0_8("exclude-0-8","--exclude-0-8"),
    v0_9("exclude-0-9", "--exclude-0-9"),
    v0_9_1("exclude-0-9-1", "--exclude-0-9-1"),
    v0_10("exclude-0-10", "--exclude-0-10"),
    v1_0("exclude-1-0", "--exclude-1-0");

    private static final Map<String, ProtocolExclusion> MAP = new HashMap<String,ProtocolExclusion>();

    static
    {
        for(ProtocolExclusion pe : ProtocolExclusion.values())
        {
            MAP.put(pe.getArg(), pe);
        }
    }

    private String _arg;
    private String _excludeName;

    private ProtocolExclusion(final String excludeName, final String arg)
    {
        _excludeName = excludeName;
        _arg = arg;
    }

    public String getArg()
    {
        return _arg;
    }

    public String getExcludeName()
    {
        return _excludeName;
    }

    public static ProtocolExclusion lookup(final String arg)
    {
        ProtocolExclusion ex = MAP.get(arg);

        if(ex == null)
        {
            throw new IllegalArgumentException(arg + " is not a valid protocol exclusion");
        }

        return ex;
    }
}
