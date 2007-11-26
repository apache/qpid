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
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.*;
import org.apache.qpid.server.cluster.policy.StandardPolicies;
import org.apache.qpid.server.cluster.replay.ReplayManager;
import org.apache.qpid.server.cluster.util.LogMessage;

import java.util.List;

public class DefaultGroupManager implements GroupManager, MemberFailureListener, BrokerFactory, StandardPolicies
{
    private static final Logger _logger = Logger.getLogger(DefaultGroupManager.class);
    private final LoadTable _loadTable;
    private final BrokerFactory _factory;
    private final ReplayManager _replayMgr;
    private final BrokerGroup _group;

    DefaultGroupManager(MemberHandle handle, BrokerFactory factory, ReplayManager replayMgr)
    {
        this(handle, factory, replayMgr, new LoadTable());
    }

    DefaultGroupManager(MemberHandle handle, BrokerFactory factory, ReplayManager replayMgr, LoadTable loadTable)
    {
        handle = SimpleMemberHandle.resolve(handle);
        _logger.info(handle);
        _loadTable = loadTable;
        _factory = factory;
        _replayMgr = replayMgr;
        _group = new BrokerGroup(handle, _replayMgr, this);
    }

    public JoinState getState()
    {
        return _group.getState();
    }

    public void addMemberhipChangeListener(MembershipChangeListener l)
    {
        _group.addMemberhipChangeListener(l);
    }

    public void removeMemberhipChangeListener(MembershipChangeListener l)
    {
        _group.removeMemberhipChangeListener(l);
    }

    public void broadcast(Sendable message) throws AMQException
    {
        for (Broker b : _group.getPeers())
        {
            b.send(message, null);
        }
    }

    public void broadcast(Sendable message, BroadcastPolicy policy, GroupResponseHandler callback) throws AMQException
    {
        GroupRequest request = new GroupRequest(message, policy, callback);
        for (Broker b : _group.getPeers())
        {
            b.invoke(request);
        }
        request.finishedSend();
    }

    public void send(MemberHandle broker, Sendable message) throws AMQException
    {
        Broker destination = findBroker(broker);
        if(destination == null)
        {
            _logger.warn(new LogMessage("Invalid destination sending {0}. {1} not known", message, broker));
        }
        else
        {
            destination.send(message, null);
            _logger.debug(new LogMessage("Sent {0} to {1}", message, broker));
        }
    }

    private void send(Broker broker, Sendable message, ResponseHandler handler) throws AMQException
    {
        broker.send(message, handler);
    }

    private void ping(Broker b) throws AMQException
    {
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        ClusterPingBody ping = new ClusterPingBody((byte)8,
                                                   (byte)0,
                                                   ClusterPingBody.getClazz((byte)8, (byte)0),
                                                   ClusterPingBody.getMethod((byte)8, (byte)0),
                                                   _group.getLocal().getDetails(),
                                                   _loadTable.getLocalLoad(),
                                                   true);
        BlockingHandler handler = new BlockingHandler();
        send(getLeader(), new SimpleBodySendable(ping), handler);
        handler.waitForCompletion();
        if (handler.failed())
        {
            if (isLeader())
            {
                handleFailure(b);
            }
            else
            {
                suspect(b);
            }
        }
        else
        {
            _loadTable.setLoad(b, ((ClusterPingBody) handler.getResponse()).load);
        }
    }

    public void handlePing(MemberHandle member, long load)
    {
        _loadTable.setLoad(findBroker(member), load);
    }

    public Member redirect()
    {
        return _loadTable.redirect();
    }

    public void establish()
    {
        _group.establish();
        _logger.info("Established cluster");
    }

    public void join(MemberHandle member) throws AMQException
    {
        member = SimpleMemberHandle.resolve(member);

        Broker leader = connectToLeader(member);
        _logger.info(new LogMessage("Connected to {0}. joining", leader));
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        ClusterJoinBody join = new ClusterJoinBody((byte)8,
                                                   (byte)0,
                                                   ClusterJoinBody.getClazz((byte)8, (byte)0),
                                                   ClusterJoinBody.getMethod((byte)8, (byte)0),
                                                   _group.getLocal().getDetails());

        send(leader, new SimpleBodySendable(join));
    }

    private Broker connectToLeader(MemberHandle member) throws AMQException
    {
        try
        {
            return _group.connectToLeader(member);
        }
        catch (Exception e)
        {
            throw new AMQException("Could not connect to leader: " + e, e);
        }
    }

    public void leave() throws AMQException
    {
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        ClusterLeaveBody leave = new ClusterLeaveBody((byte)8,
                                                      (byte)0,
                                                      ClusterLeaveBody.getClazz((byte)8, (byte)0),
                                                      ClusterLeaveBody.getMethod((byte)8, (byte)0),
                                                      _group.getLocal().getDetails());

        send(getLeader(), new SimpleBodySendable(leave));
    }

    private void suspect(MemberHandle broker) throws AMQException
    {
        if (_group.isLeader(broker))
        {
            //need new leader, if this broker is next in line it can assume leadership
            if (_group.assumeLeadership())
            {
                announceMembership();
            }
            else
            {
                _logger.warn(new LogMessage("Leader failed. Expecting {0} to succeed.", _group.getMembers().get(1)));
            }
        }
        else
        {
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            ClusterSuspectBody suspect = new ClusterSuspectBody((byte)8,
                                                                (byte)0,
                                                                ClusterSuspectBody.getClazz((byte)8, (byte)0),
                                                                ClusterSuspectBody.getMethod((byte)8, (byte)0),
                                                                broker.getDetails());

            send(getLeader(), new SimpleBodySendable(suspect));
        }
    }


    public void handleJoin(MemberHandle member) throws AMQException
    {
        _logger.info(new LogMessage("Handling join request for {0}", member));
        if(isLeader())
        {
            //connect to the host and port specified:
            Broker prospect = connectToProspect(member);
            announceMembership();
            List<AMQMethodBody> msgs = _replayMgr.replay(true);
            _logger.info(new LogMessage("Replaying {0} from leader to {1}", msgs, prospect));
            prospect.replay(msgs);
        }
        else
        {
            //pass request on to leader:
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            ClusterJoinBody request = new ClusterJoinBody((byte)8, (byte)0,
                                                          ClusterJoinBody.getClazz((byte)8, (byte)0),
                                                          ClusterJoinBody.getMethod((byte)8, (byte)0),
                                                          member.getDetails());
            
            Broker leader = getLeader();
            send(leader, new SimpleBodySendable(request));
            _logger.info(new LogMessage("Passed join request for {0} to {1}", member, leader));
        }
    }

    private Broker connectToProspect(MemberHandle member) throws AMQException
    {
        try
        {
            return _group.connectToProspect(member);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            throw new AMQException("Could not connect to prospect: " + e, e);
        }
    }

    public void handleLeave(MemberHandle member) throws AMQException
    {
        handleFailure(findBroker(member));
        announceMembership();
    }

    public void handleSuspect(MemberHandle member) throws AMQException
    {
        Broker b = findBroker(member);
        if(b != null)
        {
            //ping it to check it has failed, ping will handle failure if it has
            ping(b);
            announceMembership();
        }
    }

    public void handleSynch(MemberHandle member)
    {
        _group.synched(member);
    }

    private ClusterMembershipBody createAnnouncement(String membership)
    {
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        ClusterMembershipBody announce = new ClusterMembershipBody((byte)8, (byte)0,
                                                                   ClusterMembershipBody.getClazz((byte)8, (byte)0),
                                                                   ClusterMembershipBody.getMethod((byte)8, (byte)0),
                                                                   membership.getBytes());

        
        return announce;
    }

    private void announceMembership() throws AMQException
    {
        String membership = SimpleMemberHandle.membersToString(_group.getMembers());
        ClusterMembershipBody announce = createAnnouncement(membership);
        broadcast(new SimpleBodySendable(announce));
        _logger.info(new LogMessage("Membership announcement sent: {0}", membership));
    }

    private void handleFailure(Broker peer)
    {
        peer.remove();
        _group.remove(peer);
    }

    public void handleMembershipAnnouncement(String membership) throws AMQException
    {
        _group.setMembers(SimpleMemberHandle.stringToMembers(membership));
        _logger.info(new LogMessage("Membership announcement received: {0}", membership));
    }

    public boolean isLeader()
    {
        return _group.isLeader();
    }

    public boolean isLeader(MemberHandle handle)
    {
        return _group.isLeader(handle);
    }

    public Broker getLeader()
    {
        return _group.getLeader();
    }

    private Broker findBroker(MemberHandle handle)
    {
        return _group.findBroker(handle, false);
    }

    public Member getMember(MemberHandle handle)
    {
        return findBroker(handle);
    }

    public boolean isMember(MemberHandle member)
    {
        for (MemberHandle handle : _group.getMembers())
        {
            if (handle.matches(member))
            {
                return true;
            }
        }
        return false;
    }

    public MemberHandle getLocal()
    {
        return _group.getLocal();
    }

    public void failed(MemberHandle member)
    {
        if (isLeader())
        {
            handleFailure(findBroker(member));
            try
            {
                announceMembership();
            }
            catch (AMQException e)
            {
                _logger.error("Error announcing failure: " + e, e);
            }
        }
        else
        {
            try
            {
                suspect(member);
            }
            catch (AMQException e)
            {
                _logger.error("Error sending suspect: " + e, e);
            }
        }
    }

    public Broker create(MemberHandle handle)
    {
        Broker broker = _factory.create(handle);
        broker.addFailureListener(this);
        return broker;
    }
}
