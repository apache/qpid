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

#include "qpid/broker/MessageGroupManager.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Messages.h"
#include "qpid/broker/MessageDeque.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/framing/Array.h"
#include "qpid/framing/DeliveryProperties.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/TypeCode.h"
#include "qpid/types/Variant.h"
#include "qpid/log/Statement.h"
#include "qpid/types/Variant.h"

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


/** return an iterator to the message at position, or members.end() if not found */
MessageGroupManager::GroupState::MessageFifo::iterator
MessageGroupManager::GroupState::findMsg(const qpid::framing::SequenceNumber &position)
{
    MessageState mState(position);
    MessageFifo::iterator found = std::lower_bound(members.begin(), members.end(), mState);
    return (found->position == position) ? found : members.end();
}

void MessageGroupManager::unFree( const GroupState& state )
{
    GroupFifo::iterator pos = freeGroups.find( state.members.front().position );
    assert( pos != freeGroups.end() && pos->second == &state );
    freeGroups.erase( pos );
}

void MessageGroupManager::own( GroupState& state, const std::string& owner )
{
    state.owner = owner;
    unFree( state );
}

void MessageGroupManager::disown( GroupState& state )
{
    state.owner.clear();
    assert(state.members.size());
    assert(freeGroups.find(state.members.front().position) == freeGroups.end());
    freeGroups[state.members.front().position] = &state;
}

MessageGroupManager::GroupState& MessageGroupManager::findGroup( const Message& m )
{
    uint32_t thisMsg = m.getSequence().getValue();
    if (cachedGroup && lastMsg == thisMsg) {
        hits++;
        return *cachedGroup;
    }

    std::string group = m.getPropertyAsString(groupIdHeader);
    if (group.empty()) group = defaultGroupId; //empty group is reserved

    if (cachedGroup && group == lastGroup) {
        hits++;
        lastMsg = thisMsg;
        return *cachedGroup;
    }

    misses++;

    GroupState& found = messageGroups[group];
    if (found.group.empty())
        found.group = group;    // new group, assign name
    lastMsg = thisMsg;
    lastGroup = group;
    cachedGroup = &found;
    return found;
}


void MessageGroupManager::enqueued( const Message& m )
{
    // @todo KAG optimization - store reference to group state in QueuedMessage
    // issue: const-ness??
    GroupState& state = findGroup(m);
    GroupState::MessageState mState(m.getSequence());
    state.members.push_back(mState);
    uint32_t total = state.members.size();
    QPID_LOG( trace, "group queue " << qName <<
              ": added message to group id=" << state.group << " total=" << total );
    if (total == 1) {
        // newly created group, no owner
        assert(freeGroups.find(m.getSequence()) == freeGroups.end());
        freeGroups[m.getSequence()] = &state;
    }
}


void MessageGroupManager::acquired( const Message& m )
{
    // @todo KAG  avoid lookup: retrieve direct reference to group state from QueuedMessage
    // issue: const-ness??
    GroupState& state = findGroup(m);
    GroupState::MessageFifo::iterator gm = state.findMsg(m.getSequence());
    assert(gm != state.members.end());
    gm->acquired = true;
    state.acquired += 1;
    QPID_LOG( trace, "group queue " << qName <<
              ": acquired message in group id=" << state.group << " acquired=" << state.acquired );
}


void MessageGroupManager::requeued( const Message& m )
{
    // @todo KAG  avoid lookup: retrieve direct reference to group state from QueuedMessage
    // issue: const-ness??
    GroupState& state = findGroup(m);
    assert( state.acquired != 0 );
    state.acquired -= 1;
    GroupState::MessageFifo::iterator i = state.findMsg(m.getSequence());
    assert(i != state.members.end());
    i->acquired = false;
    if (state.acquired == 0 && state.owned()) {
        QPID_LOG( trace, "group queue " << qName <<
                  ": consumer name=" << state.owner << " released group id=" << state.group);
        disown(state);
    }
    QPID_LOG( trace, "group queue " << qName <<
              ": requeued message to group id=" << state.group << " acquired=" << state.acquired );
}


void MessageGroupManager::dequeued( const Message& m )
{
    // @todo KAG  avoid lookup: retrieve direct reference to group state from QueuedMessage
    // issue: const-ness??
    GroupState& state = findGroup(m);
    GroupState::MessageFifo::iterator i = state.findMsg(m.getSequence());
    assert(i != state.members.end());
    if (i->acquired) {
        assert( state.acquired != 0 );
        state.acquired -= 1;
    }

    // special case if qm is first (oldest) message in the group:
    // may need to re-insert it back on the freeGroups list, as the index will change
    bool reFreeNeeded = false;
    if (i == state.members.begin()) {
        if (!state.owned()) {
            // will be on the freeGroups list if mgmt is dequeueing rather than a consumer!
            // if on freelist, it is indexed by first member, which is about to be removed!
            unFree(state);
            reFreeNeeded = true;
        }
        state.members.pop_front();
    } else {
        state.members.erase(i);
    }

    uint32_t total = state.members.size();
    QPID_LOG( trace, "group queue " << qName <<
              ": dequeued message from group id=" << state.group << " total=" << total );

    if (total == 0) {
        QPID_LOG( trace, "group queue " << qName << ": deleting group id=" << state.group);
        if (cachedGroup == &state) {
            cachedGroup = 0;
        }
        std::string key(state.group);
        messageGroups.erase( key );
    } else if (state.acquired == 0 && state.owned()) {
        QPID_LOG( trace, "group queue " << qName <<
                  ": consumer name=" << state.owner << " released group id=" << state.group);
        disown(state);
        MessageDeque* md = dynamic_cast<MessageDeque*>(&messages);
        if (md) {
            md->resetCursors();
        } else {
            QPID_LOG(warning, "Could not reset cursors for message group, unexpected container type");
        }
    } else if (reFreeNeeded) {
        disown(state);
    }
}

MessageGroupManager::~MessageGroupManager()
{
    QPID_LOG( debug, "group queue " << qName << " cache results: hits=" << hits << " misses=" << misses );
}

bool MessageGroupManager::acquire(const std::string& consumer, Message& m)
{
    if (m.getState() == AVAILABLE) {
        // @todo KAG avoid lookup: retrieve direct reference to group state from QueuedMessage
        GroupState& state = findGroup(m);

        if (!state.owned()) {
            own( state, consumer );
            QPID_LOG( trace, "group queue " << qName <<
                      ": consumer name=" << consumer << " has acquired group id=" << state.group);
        }
        if (state.owner == consumer) {
            m.setState(ACQUIRED);
            return true;
        } else {
            return false;
        }
    } else {
        return false;
    }
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
        info[GROUP_MSG_COUNT] = (uint64_t) g->second.members.size();
        // set the timestamp to the arrival timestamp of the oldest (HEAD) message, if present
        info[GROUP_TIMESTAMP] = 0;
        if (g->second.members.size() != 0) {
            Message* m = messages.find(g->second.members.front().position, 0);
            if (m && m->getTimestamp()) {
                info[GROUP_TIMESTAMP] = m->getTimestamp();
            }
        }
        info[GROUP_CONSUMER] = g->second.owner;
        groups.push_back(info);
    }
    state[GROUP_STATE_KEY] = groups;
    status[GROUP_QUERY_KEY] = state;
}


boost::shared_ptr<MessageGroupManager> MessageGroupManager::create( const std::string& qName,
                                                                    Messages& messages,
                                                                    const QueueSettings& settings )
{
    boost::shared_ptr<MessageGroupManager> manager( new MessageGroupManager( settings.groupKey, qName, messages, settings.addTimestamp ) );
    QPID_LOG( debug, "Configured Queue '" << qName <<
              "' for message grouping using header key '" << settings.groupKey << "'" <<
              " (timestamp=" << settings.addTimestamp << ")");
    return manager;
}

std::string MessageGroupManager::defaultGroupId;
void MessageGroupManager::setDefaults(const std::string& groupId)   // static
{
    defaultGroupId = groupId;
}

namespace {
    const std::string GROUP_NAME("name");
    const std::string GROUP_OWNER("owner");
    const std::string GROUP_ACQUIRED_CT("acquired-ct");
    const std::string GROUP_POSITIONS("positions");
    const std::string GROUP_ACQUIRED_MSGS("acquired-msgs");
    const std::string GROUP_STATE("group-state");
}

