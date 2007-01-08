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

import junit.framework.TestCase;
import org.apache.qpid.framing.AMQShortString;

public class ClusterCapabilityTest extends TestCase
{
    public void testStartWithNull()
    {
        MemberHandle peer = new SimpleMemberHandle("myhost:9999");
        AMQShortString c = ClusterCapability.add(null, peer);
        assertTrue(ClusterCapability.contains(c));
        assertTrue(peer.matches(ClusterCapability.getPeer(c)));
    }

    public void testStartWithText()
    {
        MemberHandle peer = new SimpleMemberHandle("myhost:9999");
        AMQShortString c = ClusterCapability.add(new AMQShortString("existing text"), peer);
        assertTrue(ClusterCapability.contains(c));
        assertTrue(peer.matches(ClusterCapability.getPeer(c)));
    }
}
