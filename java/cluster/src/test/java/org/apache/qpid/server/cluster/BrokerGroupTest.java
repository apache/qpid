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

import java.io.IOException;
import java.util.Arrays;

public class BrokerGroupTest extends TestCase
{
    private final MemberHandle a = new SimpleMemberHandle("A", 1);
    private final MemberHandle b = new SimpleMemberHandle("B", 1);
    private final MemberHandle c = new SimpleMemberHandle("C", 1);
    private final MemberHandle d = new SimpleMemberHandle("D", 1);

    //join (new members perspective)
    //    (i) connectToLeader()
    //    ==> check state
    //    (ii) setMembers()
    //    ==> check state
    //    ==> check members
    //    (iii) synched(leader)
    //    ==> check state
    //    ==> check peers
    //    (iv) synched(other)
    //    ==> check state
    //    ==> check peers
    //    repeat for all others
    public void testJoin_newMember() throws Exception
    {
        MemberHandle[] pre = new MemberHandle[]{a, b, c};
        MemberHandle[] post = new MemberHandle[]{a, b, c};

        BrokerGroup group = new BrokerGroup(d, new TestReplayManager(), new TestBrokerFactory());
        assertEquals(JoinState.UNINITIALISED, group.getState());
        //(i)
        group.connectToLeader(a);
        assertEquals(JoinState.JOINING, group.getState());
        assertEquals("Wrong number of peers", 1, group.getPeers().size());
        //(ii)
        group.setMembers(Arrays.asList(post));
        assertEquals(JoinState.INITIATION, group.getState());
        assertEquals(Arrays.asList(post), group.getMembers());
        //(iii) & (iv)
        for (MemberHandle member : pre)
        {
            group.synched(member);
            if (member == c)
            {
                assertEquals(JoinState.JOINED, group.getState());
                assertEquals("Wrong number of peers", pre.length, group.getPeers().size());
            }
            else
            {
                assertEquals(JoinState.INDUCTION, group.getState());
                assertEquals("Wrong number of peers", 1, group.getPeers().size());
            }
        }
    }

    //join (leaders perspective)
    //    (i) extablish()
    //    ==> check state
    //    ==> check members
    //    ==> check peers
    //    (ii) connectToProspect()
    //    ==> check members
    //    ==> check peers
    //    repeat (ii)
    public void testJoin_Leader() throws IOException, InterruptedException
    {
        MemberHandle[] prospects = new MemberHandle[]{b, c, d};

        BrokerGroup group = new BrokerGroup(a, new TestReplayManager(), new TestBrokerFactory());
        assertEquals(JoinState.UNINITIALISED, group.getState());
        //(i)
        group.establish();
        assertEquals(JoinState.JOINED, group.getState());
        assertEquals("Wrong number of peers", 0, group.getPeers().size());
        assertEquals("Wrong number of members", 1, group.getMembers().size());
        assertEquals(a, group.getMembers().get(0));
        //(ii)
        for (int i = 0; i < prospects.length; i++)
        {
            group.connectToProspect(prospects[i]);
            assertEquals("Wrong number of peers", i + 1, group.getPeers().size());
            for (int j = 0; j <= i; j++)
            {
                assertTrue(prospects[i].matches(group.getPeers().get(i)));
            }
            assertEquals("Wrong number of members", i + 2, group.getMembers().size());
            assertEquals(a, group.getMembers().get(0));
            for (int j = 0; j <= i; j++)
            {
                assertEquals(prospects[i], group.getMembers().get(i + 1));
            }
        }
    }

    //join (general perspective)
    //    (i) set up group
    //    (ii) setMembers()
    //    ==> check members
    //    ==> check peers
    public void testJoin_general() throws Exception
    {
        MemberHandle[] view1 = new MemberHandle[]{a, b, c};
        MemberHandle[] view2 = new MemberHandle[]{a, b, c, d};
        MemberHandle[] peers = new MemberHandle[]{a, b, d};

        BrokerGroup group = new BrokerGroup(c, new TestReplayManager(), new TestBrokerFactory());
        //(i)
        group.connectToLeader(a);
        group.setMembers(Arrays.asList(view1));
        for (MemberHandle h : view1)
        {
            group.synched(h);
        }
        //(ii)
        group.setMembers(Arrays.asList(view2));
        assertEquals(Arrays.asList(view2), group.getMembers());
        assertEquals(peers.length, group.getPeers().size());
        for (int i = 0; i < peers.length; i++)
        {
            assertTrue(peers[i].matches(group.getPeers().get(i)));
        }
    }

    //leadership transfer (valid)
    //    (i) set up group
    //    (ii) assumeLeadership()
    //    ==> check return value
    //    ==> check members
    //    ==> check peers
    //    ==> check isLeader()
    //    ==> check isLeader(old_leader)
    //    ==> check isMember(old_leader)
    public void testTransferLeadership_valid() throws Exception
    {
        MemberHandle[] view1 = new MemberHandle[]{a, b};
        MemberHandle[] view2 = new MemberHandle[]{a, b, c, d};
        MemberHandle[] view3 = new MemberHandle[]{b, c, d};

        BrokerGroup group = new BrokerGroup(b, new TestReplayManager(), new TestBrokerFactory());
        //(i)
        group.connectToLeader(a);
        group.setMembers(Arrays.asList(view1));
        for (MemberHandle h : view1)
        {
            group.synched(h);
        }
        group.setMembers(Arrays.asList(view2));
        //(ii)
        boolean result = group.assumeLeadership();
        assertTrue(result);
        assertTrue(group.isLeader());
        assertFalse(group.isLeader(a));
        assertEquals(Arrays.asList(view3), group.getMembers());
        assertEquals(2, group.getPeers().size());
        assertTrue(c.matches(group.getPeers().get(0)));
        assertTrue(d.matches(group.getPeers().get(1)));
    }

    //leadership transfer (invalid)
    //    (i) set up group
    //    (ii) assumeLeadership()
    //    ==> check return value
    //    ==> check members
    //    ==> check peers
    //    ==> check isLeader()
    //    ==> check isLeader(old_leader)
    //    ==> check isMember(old_leader)
    public void testTransferLeadership_invalid() throws Exception
    {
        MemberHandle[] view1 = new MemberHandle[]{a, b, c};
        MemberHandle[] view2 = new MemberHandle[]{a, b, c, d};

        BrokerGroup group = new BrokerGroup(c, new TestReplayManager(), new TestBrokerFactory());
        //(i)
        group.connectToLeader(a);
        group.setMembers(Arrays.asList(view1));
        for (MemberHandle h : view1)
        {
            group.synched(h);
        }
        group.setMembers(Arrays.asList(view2));
        //(ii)
        boolean result = group.assumeLeadership();
        assertFalse(result);
        assertFalse(group.isLeader());
        assertTrue(group.isLeader(a));
        assertEquals(Arrays.asList(view2), group.getMembers());
        assertEquals(3, group.getPeers().size());
        assertTrue(a.matches(group.getPeers().get(0)));
        assertTrue(b.matches(group.getPeers().get(1)));
        assertTrue(d.matches(group.getPeers().get(2)));

    }

    //leave (leaders perspective)
    //    (i) set up group
    //    (ii) remove a member
    //    ==> check members
    //    ==> check peers
    //    ==> check isMember(removed_member)
    //    repeat (ii)
    public void testLeave_leader()
    {
        MemberHandle[] view1 = new MemberHandle[]{a, b, c, d};
        MemberHandle[] view2 = new MemberHandle[]{a, b, d};
        MemberHandle[] view3 = new MemberHandle[]{a, d};
        MemberHandle[] view4 = new MemberHandle[]{a};
        //(i)
        BrokerGroup group = new BrokerGroup(a, new TestReplayManager(), new TestBrokerFactory());
        group.establish();
        group.setMembers(Arrays.asList(view1));
        //(ii)
        group.remove(group.findBroker(c, false));
        assertEquals(Arrays.asList(view2), group.getMembers());

        group.remove(group.findBroker(b, false));
        assertEquals(Arrays.asList(view3), group.getMembers());

        group.remove(group.findBroker(d, false));
        assertEquals(Arrays.asList(view4), group.getMembers());
    }


    //leave (general perspective)
    //    (i) set up group
    //    (ii) setMember
    //    ==> check members
    //    ==> check peers
    //    ==> check isMember(removed_member)
    //    repeat (ii)
    public void testLeave_general()
    {
        MemberHandle[] view1 = new MemberHandle[]{a, b, c, d};
        MemberHandle[] view2 = new MemberHandle[]{a, c, d};
        //(i)
        BrokerGroup group = new BrokerGroup(c, new TestReplayManager(), new TestBrokerFactory());
        group.establish(); //not strictly the correct way to build up the group, but ok for here
        group.setMembers(Arrays.asList(view1));
        //(ii)
        group.setMembers(Arrays.asList(view2));
        assertEquals(Arrays.asList(view2), group.getMembers());
        assertEquals(2, group.getPeers().size());
        assertTrue(a.matches(group.getPeers().get(0)));
        assertTrue(d.matches(group.getPeers().get(1)));
    }
}
