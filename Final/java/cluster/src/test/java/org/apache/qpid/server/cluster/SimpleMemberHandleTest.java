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

public class SimpleMemberHandleTest extends TestCase
{
    public void testMatches()
    {
        assertMatch(new SimpleMemberHandle("localhost", 8888), new SimpleMemberHandle("localhost", 8888));
        assertNoMatch(new SimpleMemberHandle("localhost", 8889), new SimpleMemberHandle("localhost", 8888));
        assertNoMatch(new SimpleMemberHandle("localhost", 8888), new SimpleMemberHandle("localhost2", 8888));
    }

    public void testResolve()
    {
        assertEquivalent(new SimpleMemberHandle("WGLAIBD8XGR0J:9000"), new SimpleMemberHandle("localhost:9000"));
    }

    private void assertEquivalent(MemberHandle a, MemberHandle b)
    {
        String msg = a + " is not equivalent to " + b;
        a = SimpleMemberHandle.resolve(a);
        b = SimpleMemberHandle.resolve(b);
        msg += "(" + a + " does not match " + b + ")";
        assertTrue(msg, a.matches(b));
    }

    private void assertMatch(MemberHandle a, MemberHandle b)
    {
        assertTrue(a + " does not match " + b, a.matches(b));
    }

    private void assertNoMatch(MemberHandle a, MemberHandle b)
    {
        assertFalse(a + " matches " + b, a.matches(b));
    }
}
