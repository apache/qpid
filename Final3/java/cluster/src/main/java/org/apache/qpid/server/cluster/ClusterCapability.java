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
package org.apache.qpid.server.cluster;

import org.apache.qpid.framing.AMQShortString;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClusterCapability
{
    public static final String PATTERN = ".*\\bcluster_peer=(\\S*:\\d*)\b*.*";
    public static final String PEER = "cluster_peer";

    public static AMQShortString add(AMQShortString original, MemberHandle identity)
    {
        return original == null ? peer(identity) : new AMQShortString(original + " " + peer(identity));
    }

    private static AMQShortString peer(MemberHandle identity)
    {
        return new AMQShortString(PEER + "=" + identity.getDetails());
    }

    public static boolean contains(AMQShortString in)
    {
        return in != null; // && in.contains(in);
    }

    public static MemberHandle getPeer(AMQShortString in)
    {
        Matcher matcher = Pattern.compile(PATTERN).matcher(in);
        if (matcher.matches())
        {
            return new SimpleMemberHandle(matcher.group(1));
        }
        else
        {
            throw new RuntimeException("Could not find peer in '" + in + "'");
        }
    }
}
