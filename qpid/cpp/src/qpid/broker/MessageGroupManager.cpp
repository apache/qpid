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

#include "qpid/framing/FieldTable.h"
#include "qpid/types/Variant.h"
#include "qpid/log/Statement.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/MessageGroupManager.h"

using namespace qpid::broker;

namespace {
    const std::string GROUP_QUERY_KEY("qpid.message_group_queue");
    const std::string GROUP_HEADER_KEY("group_header_key");
    const std::string GROUP_STATE_KEY("group_state");
    const std::string GROUP_ID_KEY("group_id");
    const std::string GROUP_MSG_COUNT("msg_count");
    const std::string GROUP_TIMESTAMP("timestamp");
    const std::string GROUP_CONSUMER("consumer");
}


const std::string MessageGroupManager::qpidMessageGroupKey("qpid.group_header_key");
const std::string MessageGroupManager::qpidSharedGroup("qpid.shared_msg_group");
const std::string MessageGroupManager::qpidMessageGroupTimestamp("qpid.group_timestamp");


/** class ConsumerState **/

/** the consumer owns the given group */
void MessageGroupManager::ConsumerState::addGroup(const GroupState& group)
{
    ownedGroups += 1;
    pendingMsgs += (group.totalMsgs() - group.acquiredMsgs());
}

/** the consumer releases the given group */
void MessageGroupManager::ConsumerState::removeGroup(const GroupState& group)
{
    assert(ownedGroups != 0);
    ownedGroups -= 1;
    uint32_t del = (group.totalMsgs() - group.acquiredMsgs());
    assert(del <= pendingMsgs);
    pendingMsgs -= del;
}

/** notify the consumer that a new message has arrived at one if its owned groups */
void MessageGroupManager::ConsumerState::msgAvailable(const GroupState&,
                                                      const QueuedMessage& )
{
    assert(ownedGroups != 0);
    pendingMsgs += 1;
}

/** notify the consumer that an available message has been acquired */
void MessageGroupManager::ConsumerState::msgAcquired(const GroupState&,
                                                     const QueuedMessage& )
{
    assert(pendingMsgs != 0);
    pendingMsgs -= 1;
}


void MessageGroupManager::consumerAdded( const Consumer& c)
{
    const std::string& name = c.getName();
    ConsumerState& state = consumers[name];
    state.setName(name);
    state.uncancel();  // just in case old consumer resubcribed
    QPID_LOG( trace, "group queue " << qName << ": consumer " << name << " added.");
}

void MessageGroupManager::consumerRemoved( const Consumer& c)
{
    const std::string& name = c.getName();
    ConsumerMap::iterator cs = consumers.find( name );
    assert(cs != consumers.end());
    ConsumerState& state = cs->second;
    state.cancel();
    if (state.groupCount() == 0) {
        assert(state.remainingMsgs() == 0);
        consumers.erase(cs);
        QPID_LOG( trace, "group queue " << qName << ": consumer " << name << " removed.");
    }
}


/** GroupFifo */
void MessageGroupManager::GroupFifo::addGroup(const GroupState& group)
{
    assert(group.totalMsgs() != 0);
    const framing::SequenceNumber& next = group.nextMsg();
    assert(fifo.find(next) == fifo.end());
    fifo[next] = &group;
}

void MessageGroupManager::GroupFifo::removeGroup(const GroupState& group)
{
    Fifo::iterator pos = fifo.find( group.nextMsg() );
    assert( pos != fifo.end() && pos->second == &group );
    fifo.erase( pos );
}

const MessageGroupManager::GroupState& MessageGroupManager::GroupFifo::nextGroup() const
{
    return *(fifo.begin()->second);
}


/** GroupState */
void MessageGroupManager::GroupState::setOwner( ConsumerState& consumer )
{
    assert(owner == 0);
    owner = &consumer;
    owner->addGroup( *this );
}

void MessageGroupManager::GroupState::resetOwner()
{
    assert(owner);
    owner->removeGroup( *this );
    owner = 0;
}

const qpid::framing::SequenceNumber& MessageGroupManager::GroupState::nextMsg() const
{
    assert(members.size() != 0);
    return members.front();
}


void MessageGroupManager::GroupState::enqueueMsg(const QueuedMessage& msg)
{
    members.push_back(msg.position);
    if (owner) {
        owner->msgAvailable(*this, msg);
    }
}


void MessageGroupManager::GroupState::acquireMsg(const QueuedMessage& msg)
{
    assert(members.size());   // there are msgs present
    acquired += 1;
    if (owner) {
        owner->msgAcquired(*this, msg);
    }
}

void MessageGroupManager::GroupState::requeueMsg(const QueuedMessage& msg)
{
    assert(acquired != 0);
    acquired -= 1;
    if (owner) {
        owner->msgAvailable(*this, msg);
    }
}


void MessageGroupManager::GroupState::dequeueMsg(const QueuedMessage& msg)
{
    assert( members.size() != 0 );
    assert( acquired != 0 );
    acquired -= 1;

    // likely to be at or near begin() if dequeued in order
    if (members.front() == msg.position) {
        members.pop_front();
    } else {
        unsigned long diff = msg.position.getValue() - members.front().getValue();
        long maxEnd = diff < members.size() ? (diff + 1) : members.size();
        GroupState::PositionFifo::iterator i =
          std::lower_bound(members.begin(), members.begin()+maxEnd, msg.position);
        assert(i != members.end() && *i == msg.position);
        members.erase(i);
    }
}

void MessageGroupManager::GroupState::getPositions(framing::Array& positions) const
{
    for (PositionFifo::const_iterator p = members.begin();
         p != members.end(); ++p)
        positions.push_back(framing::Array::ValuePtr(new framing::IntegerValue( *p )));
}

void MessageGroupManager::GroupState::setPositions(const framing::Array& positions)
{
    members.clear();
    for (framing::Array::const_iterator p = positions.begin(); p != positions.end(); ++p)
        members.push_back((*p)->getIntegerValue<uint32_t, 4>());
}



MessageGroupManager::GroupState& MessageGroupManager::findGroup(const QueuedMessage& qm)
{
    uint32_t thisMsg = qm.position.getValue();
    if (cachedGroup && lastMsg == thisMsg) {
        hits++;
        return *cachedGroup;
    }

    std::string group = defaultGroupId;
    const qpid::framing::FieldTable* headers = qm.payload->getApplicationHeaders();
    if (headers) {
        qpid::framing::FieldTable::ValuePtr id = headers->get( groupIdHeader );
        if (id && id->convertsTo<std::string>()) {
            std::string tmp = id->get<std::string>();
            if (!tmp.empty())   // empty group is reserved
                group = tmp;
        }
    }

    if (cachedGroup && group == lastGroup) {
        hits++;
        lastMsg = thisMsg;
        return *cachedGroup;
    }

    misses++;

    cachedGroup = &messageGroups[group];
    if (cachedGroup->getName().empty())
        cachedGroup->setName(group);    // new group, assign name
    lastMsg = thisMsg;
    lastGroup = group;
    return *cachedGroup;
}


void MessageGroupManager::deleteGroup(GroupState& group)
{
    if (cachedGroup == &group)
        cachedGroup = 0;
    std::string name = group.getName();
    messageGroups.erase(name);
}

void MessageGroupManager::enqueued( const QueuedMessage& qm )
{
    // @todo KAG optimization - store reference to group state in QueuedMessage
    // issue: const-ness??
    GroupState& state = findGroup(qm);
    state.enqueueMsg(qm);
    uint32_t total = state.totalMsgs();
    QPID_LOG( trace, "group queue " << qName <<
              ": added message to group id=" << state.getName() << " total=" << total );
    if (total == 1) {
        // newly created group, no owner
        freeGroups.addGroup(state);
    }
}


void MessageGroupManager::acquired( const QueuedMessage& qm )
{
    GroupState& state = findGroup(qm);
    state.acquireMsg(qm);
    QPID_LOG( trace, "group queue " << qName <<
              ": acquired message in group id=" << state.getName() << " acquired=" << state.acquiredMsgs());
}


void MessageGroupManager::requeued( const QueuedMessage& qm )
{
    // @todo KAG  avoid lookup: retrieve direct reference to group state from QueuedMessage
    // issue: const-ness??
    GroupState& state = findGroup(qm);
    state.requeueMsg(qm);
    if (state.acquiredMsgs() == 0 && state.getOwner()) {
        disownGroup(state);
        freeGroups.addGroup(state);
    }
    QPID_LOG( trace, "group queue " << qName <<
              ": requeued message to group id=" << state.getName() << " acquired=" << state.acquiredMsgs());
}


void MessageGroupManager::dequeued( const QueuedMessage& qm )
{
    GroupState& group = findGroup(qm);
    bool freeNeeded = false;
    if (group.isFree()) {       // dequeue is occuring via mgmt, not subscriber!
        const framing::SequenceNumber next = group.nextMsg();
        if (next == qm.position) {
            /* we are about to remove the head message of this group.  This message is
             * used to index the freeGroups fifo, so we must temporarily remove it from
             * the fifo until we are done updating the head message.
             */
            freeGroups.removeGroup(group);
            freeNeeded = true;
        }
    }
    group.dequeueMsg(qm);

    uint32_t total = group.totalMsgs();
    QPID_LOG( trace, "group queue " << qName <<
              ": dequeued message from group id=" << group.getName() << " total=" << total );

    // if no more outstanding acquired messages, free the group from the consumer
    if (group.acquiredMsgs() == 0 && group.getOwner()) {
        // group is now available again
        disownGroup(group);
        freeNeeded = true;
    }

    QPID_LOG( trace, "group queue " << qName <<
              ": dequeued message from group id=" << group.getName() << " total=" << total );

    if (total == 0) {
        QPID_LOG( trace, "group queue " << qName << ": deleting group id=" << group.getName());
        deleteGroup(group);
    } else if (freeNeeded) {
        freeGroups.addGroup(group);
    }
}

/** remove the owner of the group */
void MessageGroupManager::disownGroup(GroupState& group)
{
    ConsumerState& owner = *group.getOwner();
    QPID_LOG( trace, "group queue " << qName <<
              ": consumer name=" << owner.getName() << " released group id=" << group.getName());
    group.resetOwner();
    if (owner.cancelled() && owner.groupCount() == 0) {
        // this owner has unsubscribed, we can release it now.
        std::string name = owner.getName();
        consumers.erase(name);
        QPID_LOG( error, "group queue " << qName << ": consumer " << name << " removed.");
    }
}

namespace {
    unsigned long found = 0;
    unsigned long failed = 0;
    unsigned long missCount = 0;
    unsigned long earlyRet = 0;
}

MessageGroupManager::~MessageGroupManager()
{
    QPID_LOG( debug, "group queue " << qName << " cache results: hits=" << hits << " misses=" << misses );
}
bool MessageGroupManager::nextConsumableMessage( Consumer::shared_ptr& c, QueuedMessage& next )
{
    if (messages.empty())
        return false;

    ConsumerState& cState = consumers.find(c->getName())->second;

    next.position = c->position;
    if (freeGroups.groupCount() != 0) {
        const framing::SequenceNumber& nextFree = freeGroups.nextGroup().nextMsg();
        if (nextFree <= next.position) {     // a free message is older than current
            next.position = nextFree;
            --next.position;
        }
    } else if (cState.remainingMsgs() == 0) {   // no more msgs from owned groups
        earlyRet += 1;
        return false;
    }

    int count = 1;
    while (messages.next( next.position, next )) {
        GroupState& group = findGroup(next);
        if (group.getOwner() == &cState) {
            found += 1;
            return true;
        } else if (group.isFree()) {
            if (group.nextMsg() == next.position) {    // only take from head!
                found += 1;
                return true;
            }
            QPID_LOG(debug, "Skipping " << next.position << " since group " << group.getName()
                     << "'s head message still pending. pos=" << group.nextMsg());
        }
        count += 1;
    }
    failed += 1;
    missCount += 1;
    return false;
}


bool MessageGroupManager::allocate(const std::string& consumer, const QueuedMessage& qm)
{
    // @todo KAG avoid lookup: retrieve direct reference to group state from QueuedMessage
    GroupState& state = findGroup(qm);

    if (state.isFree()) {
        freeGroups.removeGroup(state);
        ConsumerMap::iterator cs = consumers.find( consumer );
        assert(cs != consumers.end());
        ConsumerState& owner = cs->second;
        state.setOwner( owner );
        QPID_LOG( trace, "group queue " << qName <<
                  ": consumer name=" << consumer << " has acquired group id=" << state.getName());
        return true;
    }
    return state.getOwner()->getName() == consumer;
}

bool MessageGroupManager::nextBrowsableMessage( Consumer::shared_ptr& c, QueuedMessage& next )
{
    // browse: allow access to any available msg, regardless of group ownership (?ok?)
    if (!messages.empty() && messages.next(c->position, next))
        return true;
    return false;
}

void MessageGroupManager::query(qpid::types::Variant::Map& status) const
{
    /** Add a description of the current state of the message groups for this queue.
        FORMAT:
        { "qpid.message_group_queue":
            { "group_header_key" : "<KEY>",
              "group_state" :
                   [ { "group_id"  : "<name>",
                       "msg_count" : <int>,
                       "timestamp" : <absTime>,
                       "consumer"  : <consumer name> },
                     {...} // one for each known group
                   ]
            }
        }
    **/

    assert(status.find(GROUP_QUERY_KEY) == status.end());
    qpid::types::Variant::Map state;
    qpid::types::Variant::List groups;

    state[GROUP_HEADER_KEY] = groupIdHeader;
    for (GroupMap::const_iterator g = messageGroups.begin();
         g != messageGroups.end(); ++g) {
        qpid::types::Variant::Map info;
        info[GROUP_ID_KEY] = g->first;
        info[GROUP_MSG_COUNT] = g->second.totalMsgs();
        info[GROUP_TIMESTAMP] = 0;   /** @todo KAG - NEED HEAD MSG TIMESTAMP */
        if (g->second.getOwner()) {
            info[GROUP_CONSUMER] = g->second.getOwner()->getName();
        } else {
            info[GROUP_CONSUMER] = std::string("");
        }
        groups.push_back(info);
    }
    state[GROUP_STATE_KEY] = groups;
    status[GROUP_QUERY_KEY] = state;
}


boost::shared_ptr<MessageGroupManager> MessageGroupManager::create( const std::string& qName,
                                                                    Messages& messages,
                                                                    const qpid::framing::FieldTable& settings )
{
    boost::shared_ptr<MessageGroupManager> empty;

    if (settings.isSet(qpidMessageGroupKey)) {

        // @todo: remove once "sticky" consumers are supported - see QPID-3347
        if (!settings.isSet(qpidSharedGroup)) {
            QPID_LOG( error, "Only shared groups are supported in this version of the broker. Use '--shared-groups' in qpid-config." );
            return empty;
        }

        std::string headerKey = settings.getAsString(qpidMessageGroupKey);
        if (headerKey.empty()) {
            QPID_LOG( error, "A Message Group header key must be configured, queue=" << qName);
            return empty;
        }
        unsigned int timestamp = settings.getAsInt(qpidMessageGroupTimestamp);

        boost::shared_ptr<MessageGroupManager> manager( new MessageGroupManager( headerKey, qName, messages, timestamp ) );

        QPID_LOG( debug, "Configured Queue '" << qName <<
                  "' for message grouping using header key '" << headerKey << "'" <<
                  " (timestamp=" << timestamp << ")");
        return manager;
    }
    return empty;
}

std::string MessageGroupManager::defaultGroupId;
void MessageGroupManager::setDefaults(const std::string& groupId)   // static
{
    defaultGroupId = groupId;
}

/** Cluster replication:

   state map format:

   { "group-state": [ {"name": <group-name>,
                       "owner": <consumer-name>-or-empty,
                       "acquired-ct": <acquired count>,
                       "positions": [Seqnumbers, ... ]},
                      {...}
                    ]
   }
*/

namespace {
    const std::string GROUP_NAME("name");
    const std::string GROUP_OWNER("owner");
    const std::string GROUP_ACQUIRED_CT("acquired-ct");
    const std::string GROUP_POSITIONS("positions");
    const std::string GROUP_STATE("group-state");
    const std::string OWNER_STATE("owner-state");
    const std::string CANCELLED("cancelled");
    const std::string YES("yes");
    const std::string NO("no");
    const std::string OWNER_NAME("name");
}


/** Runs on UPDATER to snapshot current state */
void MessageGroupManager::getState(qpid::framing::FieldTable& state ) const
{
    using namespace qpid::framing;
    state.clear();
    framing::Array groupState(TYPE_CODE_MAP);
    for (GroupMap::const_iterator g = messageGroups.begin();
         g != messageGroups.end(); ++g) {

        framing::FieldTable group;
        group.setString(GROUP_NAME, g->first);
        if (g->second.getOwner()) {
            group.setString(GROUP_OWNER, g->second.getOwner()->getName());
        } else {
            group.setString(GROUP_OWNER, std::string(""));
        }
        group.setInt(GROUP_ACQUIRED_CT, g->second.acquiredMsgs());
        framing::Array positions(TYPE_CODE_UINT32);
        g->second.getPositions(positions);
        group.setArray(GROUP_POSITIONS, positions);
        groupState.push_back(framing::Array::ValuePtr(new FieldTableValue(group)));
    }
    state.setArray(GROUP_STATE, groupState);

    framing::Array ownerState(TYPE_CODE_MAP);
    for (ConsumerMap::const_iterator c = consumers.begin();
         c != consumers.end(); ++c) {
        framing::FieldTable owner;
        owner.setString(OWNER_NAME, c->first);
        owner.setString(CANCELLED, c->second.cancelled() ? YES : NO);
        ownerState.push_back(framing::Array::ValuePtr(new FieldTableValue(owner)));
    }
    state.setArray(OWNER_STATE, ownerState);

    QPID_LOG(debug, "Queue \"" << qName << "\": replicating message group state, key=" << groupIdHeader);
}


/** called on UPDATEE to set state from snapshot */
void MessageGroupManager::setState(const qpid::framing::FieldTable& state)
{
    using namespace qpid::framing;
    consumers.clear();
    messageGroups.clear();
    freeGroups.clear();
    cachedGroup = 0;

    // set up the known owners
    framing::Array ownerState(TYPE_CODE_MAP);
    bool ok = state.getArray(OWNER_STATE, ownerState);
    if (!ok) {
        QPID_LOG(error, "Unable to find message group owner state information for queue \"" <<
                 qName << "\": cluster inconsistency error!");
        return;
    }

    for (framing::Array::const_iterator c = ownerState.begin(); c != ownerState.end(); ++c) {
        framing::FieldTable ownerMap;
        ok = framing::getEncodedValue<FieldTable>(*c, ownerMap);
        if (!ok) {
            QPID_LOG(error, "Invalid message group owner information for queue \"" <<
                     qName << "\": table encoding error!");
            return;
        }
        if (!ownerMap.isSet(OWNER_NAME) || !ownerMap.isSet(CANCELLED)) {
            QPID_LOG(error, "Invalid message group owner information for queue \"" <<
                     qName << "\": fields missing error!");
            return;
        }

        const std::string name = ownerMap.getAsString(OWNER_NAME);
        ConsumerState& owner = consumers[name];
        owner.setName(name);
        if (ownerMap.getAsString(CANCELLED) == YES) {
            owner.cancel();
        }
    }

    // set up the known groups
    framing::Array groupState(TYPE_CODE_MAP);
    ok = state.getArray(GROUP_STATE, groupState);
    if (!ok) {
        QPID_LOG(error, "Unable to find message group state information for queue \"" <<
                 qName << "\": cluster inconsistency error!");
        return;
    }

    for (framing::Array::const_iterator g = groupState.begin(); g != groupState.end(); ++g) {
        framing::FieldTable groupMap;
        ok = framing::getEncodedValue<FieldTable>(*g, groupMap);
        if (!ok) {
            QPID_LOG(error, "Invalid message group state information for queue \"" <<
                     qName << "\": table encoding error!");
            return;
        }
        if (!groupMap.isSet(GROUP_NAME) || !groupMap.isSet(GROUP_OWNER) || !groupMap.isSet(GROUP_ACQUIRED_CT)) {
            QPID_LOG(error, "Invalid message group state information for queue \"" <<
                     qName << "\": fields missing error!");
            return;
        }

        // replicate the group state
        std::string name = groupMap.getAsString(GROUP_NAME);
        MessageGroupManager::GroupState& group = messageGroups[name];
        assert(group.getName().empty());
        group.setName(name);
        group.setAcquired(groupMap.getAsInt(GROUP_ACQUIRED_CT));
        framing::Array positions(TYPE_CODE_UINT32);
        ok = groupMap.getArray(GROUP_POSITIONS, positions);
        if (!ok) {
            QPID_LOG(error, "Invalid message group state information for queue \"" <<
                     qName << "\": position encoding error!");
            return;
        }
        group.setPositions(positions);

        const std::string ownerName = groupMap.getAsString(GROUP_OWNER);
        if (!ownerName.empty()) {
            ConsumerState& owner = consumers[ownerName];
            group.setOwner(owner);
        } else {
            freeGroups.addGroup(group);
        }
    }

    QPID_LOG(debug, "Queue \"" << qName << "\": message group state replicated, key =" << groupIdHeader)
}
