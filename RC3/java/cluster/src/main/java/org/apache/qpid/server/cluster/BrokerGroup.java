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

import org.apache.log4j.Logger;
import org.apache.qpid.server.cluster.replay.ReplayManager;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.server.cluster.util.InvokeMultiple;
import org.apache.qpid.framing.AMQMethodBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the membership list of a group and the set of brokers representing the
 * remote peers. The group should be initialised through a call to establish()
 * or connectToLeader().
 *
 */
class BrokerGroup
{
    private static final Logger _logger = Logger.getLogger(BrokerGroup.class);

    private final InvokeMultiple<MembershipChangeListener> _changeListeners = new InvokeMultiple<MembershipChangeListener>(MembershipChangeListener.class);
    private final ReplayManager _replayMgr;
    private final MemberHandle _local;
    private final BrokerFactory _factory;
    private final Object _lock = new Object();
    private final Set<MemberHandle> _synch = new HashSet<MemberHandle>();
    private List<MemberHandle> _members;
    private List<Broker> _peers = new ArrayList<Broker>();
    private JoinState _state = JoinState.UNINITIALISED;

    /**
     * Creates an unitialised group.
     *
     * @param local a handle that represents the local broker
     * @param replayMgr the replay manager to use when creating new brokers
     * @param factory the factory through which broker instances are created
     */
    BrokerGroup(MemberHandle local, ReplayManager replayMgr, BrokerFactory factory)
    {
        _replayMgr = replayMgr;
        _local = local;
        _factory = factory;
    }

    /**
     * Called to establish the local broker as the leader of a new group
     */
    void establish()
    {
        synchronized (_lock)
        {
            setState(JoinState.JOINED);
            _members = new ArrayList<MemberHandle>();
            _members.add(_local);
        }
        fireChange();
    }

    /**
     * Called by prospect to connect to group
     */
    Broker connectToLeader(MemberHandle handle) throws Exception
    {
        Broker leader = _factory.create(handle);
        leader = leader.connectToCluster();
        synchronized (_lock)
        {
            setState(JoinState.JOINING);
            _members = new ArrayList<MemberHandle>();
            _members.add(leader);
            _peers.add(leader);
        }
        fireChange();
        return leader;
    }

    /**
     * Called by leader when handling a join request
     */
    Broker connectToProspect(MemberHandle handle) throws IOException, InterruptedException
    {
        Broker prospect = _factory.create(handle);
        prospect.connect();
        synchronized (_lock)
        {
            _members.add(prospect);
            _peers.add(prospect);
        }
        fireChange();
        return prospect;
    }

    /**
     * Called in reponse to membership announcements.
     *
     * @param members the list of members now part of the group
     */
    void setMembers(List<MemberHandle> members)
    {
        if (isJoined())
        {
            List<Broker> old = _peers;

            synchronized (_lock)
            {
                _peers = getBrokers(members);
                _members = new ArrayList<MemberHandle>(members);
            }

            //remove those that are still members
            old.removeAll(_peers);

            //handle failure of any brokers that haven't survived
            for (Broker peer : old)
            {
                peer.remove();
            }
        }
        else
        {
            synchronized (_lock)
            {
                setState(JoinState.INITIATION);
                _members = new ArrayList<MemberHandle>(members);
                _synch.addAll(_members);
                _synch.remove(_local);
            }
        }
        fireChange();
    }

    List<MemberHandle> getMembers()
    {
        synchronized (_lock)
        {
            return Collections.unmodifiableList(_members);
        }
    }

    List<Broker> getPeers()
    {
        synchronized (_lock)
        {
            return _peers;
        }
    }

    /**
     * Removes the member presented from the group
     * @param peer the broker that should be removed
     */
    void remove(Broker peer)
    {
        synchronized (_lock)
        {
            _peers.remove(peer);
            _members.remove(peer);
        }
        fireChange();
    }

    MemberHandle getLocal()
    {
        return _local;
    }

    Broker getLeader()
    {
        synchronized (_lock)
        {
            return _peers.size() > 0 ? _peers.get(0) : null;
        }
    }

    /**
     * Allows a Broker instance to be retrieved for a given handle
     *
     * @param handle the handle for which a broker is sought
     * @param create flag to indicate whther a broker should be created for the handle if
     * one is not found within the list of known peers
     * @return the broker corresponding to handle or null if a match cannot be found and
     * create is false
     */
    Broker findBroker(MemberHandle handle, boolean create)
    {
        if (handle instanceof Broker)
        {
            return (Broker) handle;
        }
        else
        {
            for (Broker b : getPeers())
            {
                if (b.matches(handle))
                {
                    return b;
                }
            }
        }
        if (create)
        {
            Broker b = _factory.create(handle);
            List<AMQMethodBody> msgs = _replayMgr.replay(isLeader(_local));
            _logger.info(new LogMessage("Replaying {0} from {1} to {2}", msgs, _local, b));
            b.connectAsynch(msgs);

            return b;
        }
        else
        {
            return null;
        }
    }

    /**
     * @param member the member to test for leadership
     * @return true if the passed in member is the group leader, false otherwise
     */
    boolean isLeader(MemberHandle member)
    {
        synchronized (_lock)
        {
            return member.matches(_members.get(0));
        }
    }

    /**
     * @return true if the local broker is the group leader, false otherwise
     */
    boolean isLeader()
    {
        return isLeader(_local);
    }

    /**
     * Used when the leader fails and the next broker in the list needs to
     * assume leadership
     * @return true if the action succeeds
     */
    boolean assumeLeadership()
    {
        boolean valid;
        synchronized (_lock)
        {
            valid = _members.size() > 1 && _local.matches(_members.get(1));
            if (valid)
            {
                _members.remove(0);
                _peers.remove(0);
            }
        }
        fireChange();
        return valid;
    }

    /**
     * Called in response to a Cluster.Synch message being received during the join
     * process. This indicates that the member mentioned has replayed all necessary
     * messages to the local broker.
     *
     * @param member the member from whom the synch messages was received
     */
    void synched(MemberHandle member)
    {
        _logger.info(new LogMessage("Synchronised with {0}", member));
        synchronized (_lock)
        {
            if (isLeader(member))
            {
                setState(JoinState.INDUCTION);
            }
            _synch.remove(member);
            if (_synch.isEmpty())
            {
                _peers = getBrokers(_members);
                setState(JoinState.JOINED);
            }
        }
    }


    /**
     * @return the state of the group
     */
    JoinState getState()
    {
        synchronized (_lock)
        {
            return _state;
        }
    }

    void addMemberhipChangeListener(MembershipChangeListener l)
    {
        _changeListeners.addListener(l);
    }

    void removeMemberhipChangeListener(MembershipChangeListener l)
    {
        _changeListeners.removeListener(l);
    }



    private void setState(JoinState state)
    {
        _logger.info(new LogMessage("Changed state from {0} to {1}", _state, state));
        _state = state;
    }

    private boolean isJoined()
    {
        return inState(JoinState.JOINED);
    }

    private boolean inState(JoinState state)
    {
        return _state.equals(state);
    }

    private List<Broker> getBrokers(List<MemberHandle> handles)
    {
        List<Broker> brokers = new ArrayList<Broker>();
        for (MemberHandle handle : handles)
        {
            if (!_local.matches(handle))
            {
                brokers.add(findBroker(handle, true));
            }
        }
        return brokers;
    }

    private void fireChange()
    {
        List<MemberHandle> members;
        synchronized(this)
        {
            members = new ArrayList(_members);
        }
        _changeListeners.getProxy().changed(Collections.unmodifiableList(members));
    }
}