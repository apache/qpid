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

import org.apache.qpid.AMQException;

public interface GroupManager
{
    /**
     * Establish a new cluster with the local member as the leader.
     */
    public void establish();

    /**
     * Join the cluster to which member belongs 
     */
    public void join(MemberHandle member) throws AMQException;

    public void broadcast(Sendable message) throws AMQException;

    public void broadcast(Sendable message, BroadcastPolicy policy, GroupResponseHandler callback) throws AMQException;

    public void send(MemberHandle broker, Sendable message) throws AMQException;

    public void leave() throws AMQException;

    public void handleJoin(MemberHandle member) throws AMQException;

    public void handleLeave(MemberHandle member) throws AMQException;

    public void handleSuspect(MemberHandle member) throws AMQException;

    public void handlePing(MemberHandle member, long load);

    public void handleMembershipAnnouncement(String membership) throws AMQException;

    public void handleSynch(MemberHandle member);

    public boolean isLeader();

    public boolean isLeader(MemberHandle handle);

    public boolean isMember(MemberHandle member);

    public MemberHandle redirect();

    public MemberHandle getLocal();

    public JoinState getState();

    public void addMemberhipChangeListener(MembershipChangeListener l);

    public void removeMemberhipChangeListener(MembershipChangeListener l);
}
