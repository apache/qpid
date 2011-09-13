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
    const std::string GroupQueryKey("qpid.message_group_queue");
    const std::string GroupHeaderKey("group_header_key");
    const std::string GroupStateKey("group_state");
    const std::string GroupIdKey("group_id");
    const std::string GroupMsgCount("msg_count");
    const std::string GroupTimestamp("timestamp");
    const std::string GroupConsumer("consumer");
}


const std::string MessageGroupManager::qpidMessageGroupKey("qpid.group_header_key");
const std::string MessageGroupManager::qpidMessageGroupTimestamp("qpid.group_timestamp");
const std::string MessageGroupManager::qpidMessageGroupDefault("qpid.no_group");     /** @todo KAG: make configurable in Broker options */


const std::string MessageGroupManager::getGroupId( const QueuedMessage& qm ) const
{
    const qpid::framing::FieldTable* headers = qm.payload->getApplicationHeaders();
    if (!headers) return qpidMessageGroupDefault;
    qpid::framing::FieldTable::ValuePtr id = headers->get( groupIdHeader );
    if (!id || !id->convertsTo<std::string>()) return qpidMessageGroupDefault;
    return id->get<std::string>();
}


void MessageGroupManager::enqueued( const QueuedMessage& qm )
{
    // @todo KAG optimization - store reference to group state in QueuedMessage
    // issue: const-ness??
    std::string group( getGroupId(qm) );
    GroupState &state(messageGroups[group]);
    state.members.push_back(qm.position);
    uint32_t total = state.members.size();
    QPID_LOG( trace, "group queue " << queue->getName() <<
              ": added message to group id=" << group << " total=" << total );
    if (total == 1) {
        // newly created group, no owner
        state.group = group;
#ifdef NDEBUG
        freeGroups[qm.position] = &state;
#else
        bool unique = freeGroups.insert(GroupFifo::value_type(qm.position, &state)).second;
        (void) unique; assert(unique);
#endif
    }
}


void MessageGroupManager::acquired( const QueuedMessage& qm )
{
    // @todo KAG  avoid lookup: retrieve direct reference to group state from QueuedMessage
    // issue: const-ness??
    std::string group( getGroupId(qm) );
    GroupMap::iterator gs = messageGroups.find( group );
    assert( gs != messageGroups.end() );
    GroupState& state( gs->second );
    state.acquired += 1;
    QPID_LOG( trace, "group queue " << queue->getName() <<
              ": acquired message in group id=" << group << " acquired=" << state.acquired );
}


void MessageGroupManager::requeued( const QueuedMessage& qm )
{
    // @todo KAG  avoid lookup: retrieve direct reference to group state from QueuedMessage
    // issue: const-ness??
    // @todo KAG BUG - how to ensure requeue happens in the correct order?
    // @todo KAG BUG - if requeue is not in correct order - what do we do?  throw?
    std::string group( getGroupId(qm) );
    GroupMap::iterator gs = messageGroups.find( group );
    assert( gs != messageGroups.end() );
    GroupState& state( gs->second );
    assert( state.acquired != 0 );
    state.acquired -= 1;
    if (state.acquired == 0 && state.owned()) {
        QPID_LOG( trace, "group queue " << queue->getName() <<
                  ": consumer name=" << state.owner << " released group id=" << gs->first);
        disown(state);
    }
    QPID_LOG( trace, "group queue " << queue->getName() <<
              ": requeued message to group id=" << group << " acquired=" << state.acquired );
}


void MessageGroupManager::dequeued( const QueuedMessage& qm )
{
    // @todo KAG  avoid lookup: retrieve direct reference to group state from QueuedMessage
    // issue: const-ness??
    std::string group( getGroupId(qm) );
    GroupMap::iterator gs = messageGroups.find( group );
    assert( gs != messageGroups.end() );
    GroupState& state( gs->second );
    assert( state.members.size() != 0 );

    // likely to be at or near begin() if dequeued in order
    {
        GroupState::PositionFifo::iterator pos = state.members.begin();
        GroupState::PositionFifo::iterator end = state.members.end();
        while (pos != end) {
            if (*pos == qm.position) {
                state.members.erase(pos);
                break;
            }
            ++pos;
        }
    }

    assert( state.acquired != 0 );
    state.acquired -= 1;
    uint32_t total = state.members.size();
    if (total == 0) {
        if (!state.owned()) {  // unlikely, but need to remove from the free list before erase
            unFree( state );
        }
        QPID_LOG( trace, "group queue " << queue->getName() << ": deleting group id=" << gs->first);
        messageGroups.erase( gs );
    } else {
        if (state.acquired == 0 && state.owned()) {
            QPID_LOG( trace, "group queue " << queue->getName() <<
                      ": consumer name=" << state.owner << " released group id=" << gs->first);
            disown(state);
        }
    }
    QPID_LOG( trace, "group queue " << queue->getName() <<
              ": dequeued message from group id=" << group << " total=" << total );
}

void MessageGroupManager::consumerAdded( const Consumer& c )
{
    assert(consumers.find(c.getName()) == consumers.end());
    consumers[c.getName()] = 0;     // no groups owned yet
    QPID_LOG( trace, "group queue " << queue->getName() << ": added consumer, name=" << c.getName() );
}

void MessageGroupManager::consumerRemoved( const Consumer& c )
{
    const std::string& name(c.getName());
    Consumers::iterator consumer = consumers.find(name);
    assert(consumer != consumers.end());
    size_t count = consumer->second;

    for (GroupMap::iterator gs = messageGroups.begin();
         count && gs != messageGroups.end(); ++gs) {

        GroupState& state( gs->second );
        if (state.owner == name) {
            --count;
            disown(state);
            QPID_LOG( trace, "group queue " << queue->getName() <<
                      ": consumer name=" << name << " released group id=" << gs->first);
        }
    }
    consumers.erase( consumer );
    QPID_LOG( trace, "group queue " << queue->getName() << ": removed consumer name=" << name );
}


bool MessageGroupManager::nextConsumableMessage( Consumer::shared_ptr& c, QueuedMessage& next,
                                                 const qpid::sys::Mutex::ScopedLock& )
{
    Messages& messages(queue->getMessages());

    if (messages.empty())
        return false;

    if (!freeGroups.empty()) {
        framing::SequenceNumber nextFree = freeGroups.begin()->first;
        if (nextFree < c->position) {  // next free group's msg is older than current position
            bool ok = messages.find(nextFree, next);
            (void) ok; assert( ok );
        } else {
            if (!messages.next( c->position, next ))
                return false;           // shouldn't happen - should find nextFree
        }
    } else {  // no free groups available
        if (consumers[c->getName()] == 0) {  // and none currently owned
            return false;       // so nothing available to consume
        }
        if (!messages.next( c->position, next ))
            return false;
    }

    do {
        // @todo KAG  avoid lookup: retrieve direct reference to group state from QueuedMessage
        std::string group( getGroupId( next ) );
        GroupMap::iterator gs = messageGroups.find( group );
        assert( gs != messageGroups.end() );
        GroupState& state( gs->second );
        if (!state.owned() || state.owner == c->getName()) {
            return true;
        }
    } while (messages.next( next.position, next ));
    return false;
}


bool MessageGroupManager::acquirable(const std::string& consumer, const QueuedMessage& qm,
                                     const qpid::sys::Mutex::ScopedLock&)
{
    // @todo KAG avoid lookup: retrieve direct reference to group state from QueuedMessage
    std::string group( getGroupId(qm) );
    GroupMap::iterator gs = messageGroups.find( group );
    assert( gs != messageGroups.end() );
    GroupState& state( gs->second );

    if (!state.owned()) {
        own( state, consumer );
        QPID_LOG( trace, "group queue " << queue->getName() <<
                  ": consumer name=" << consumer << " has acquired group id=" << gs->first);
        return true;
    }
    return state.owner == consumer;
}


void MessageGroupManager::query(qpid::types::Variant::Map& status,
                                const qpid::sys::Mutex::ScopedLock&) const
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

    assert(status.find(GroupQueryKey) == status.end());
    qpid::types::Variant::Map state;
    qpid::types::Variant::List groups;

    state[GroupHeaderKey] = groupIdHeader;
    for (GroupMap::const_iterator g = messageGroups.begin();
         g != messageGroups.end(); ++g) {
        qpid::types::Variant::Map info;
        info[GroupIdKey] = g->first;
        info[GroupMsgCount] = g->second.members.size();
        info[GroupTimestamp] = 0;   /** @todo KAG - NEED HEAD MSG TIMESTAMP */
        info[GroupConsumer] = g->second.owner;
        groups.push_back(info);
    }
    state[GroupStateKey] = groups;
    status[GroupQueryKey] = state;
}


boost::shared_ptr<MessageGroupManager> MessageGroupManager::create( Queue *q,
                                                                    const qpid::framing::FieldTable& settings )
{
    boost::shared_ptr<MessageGroupManager> empty;

    if (settings.isSet(qpidMessageGroupKey)) {

        std::string headerKey = settings.getAsString(qpidMessageGroupKey);
        if (headerKey.empty()) {
            QPID_LOG( error, "A Message Group header key must be configured, queue=" << q->getName());
            return empty;
        }
        unsigned int timestamp = settings.getAsInt(qpidMessageGroupTimestamp);

        boost::shared_ptr<MessageGroupManager> manager( new MessageGroupManager( headerKey, q, timestamp ) );

        q->addObserver( boost::static_pointer_cast<QueueObserver>(manager) );

        QPID_LOG( debug, "Configured Queue '" << q->getName() <<
                  "' for message grouping using header key '" << headerKey << "'" <<
                  " (timestamp=" << timestamp << ")");
        return manager;
    }
    return empty;
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
        group.setString(GROUP_OWNER, g->second.owner);
        group.setInt(GROUP_ACQUIRED_CT, g->second.acquired);
        framing::Array positions(TYPE_CODE_UINT32);
        for (GroupState::PositionFifo::const_iterator p = g->second.members.begin();
             p != g->second.members.end(); ++p)
            positions.push_back(framing::Array::ValuePtr(new IntegerValue( *p )));
        group.setArray(GROUP_POSITIONS, positions);
        groupState.push_back(framing::Array::ValuePtr(new FieldTableValue(group)));
    }
    state.setArray(GROUP_STATE, groupState);

    QPID_LOG(debug, "Queue \"" << queue->getName() << "\": replicating message group state, key=" << groupIdHeader);
}


/** called on UPDATEE to set state from snapshot */
void MessageGroupManager::setState(const qpid::framing::FieldTable& state)
{
    using namespace qpid::framing;
    messageGroups.clear();
    consumers.clear();
    freeGroups.clear();

    framing::Array groupState(TYPE_CODE_MAP);

    bool ok = state.getArray(GROUP_STATE, groupState);
    if (!ok) {
        QPID_LOG(error, "Unable to find message group state information for queue \"" <<
                 queue->getName() << "\": cluster inconsistency error!");
        return;
    }

    for (framing::Array::const_iterator g = groupState.begin();
         g != groupState.end(); ++g) {
        framing::FieldTable group;
        ok = framing::getEncodedValue<FieldTable>(*g, group);
        if (!ok) {
            QPID_LOG(error, "Invalid message group state information for queue \"" <<
                     queue->getName() << "\": table encoding error!");
            return;
        }
        MessageGroupManager::GroupState state;
        if (!group.isSet(GROUP_NAME) || !group.isSet(GROUP_OWNER) || !group.isSet(GROUP_ACQUIRED_CT)) {
            QPID_LOG(error, "Invalid message group state information for queue \"" <<
                     queue->getName() << "\": fields missing error!");
            return;
        }
        state.group = group.getAsString(GROUP_NAME);
        state.owner = group.getAsString(GROUP_OWNER);
        state.acquired = group.getAsInt(GROUP_ACQUIRED_CT);
        framing::Array positions(TYPE_CODE_UINT32);
        ok = group.getArray(GROUP_POSITIONS, positions);
        if (!ok) {
            QPID_LOG(error, "Invalid message group state information for queue \"" <<
                     queue->getName() << "\": position encoding error!");
            return;
        }

        for (Array::const_iterator p = positions.begin(); p != positions.end(); ++p)
            state.members.push_back((*p)->getIntegerValue<uint32_t, 4>());
        messageGroups[state.group] = state;
        if (state.owned())
            consumers[state.owner]++;
        else {
            assert(state.members.size());
            freeGroups[state.members.front()] = &messageGroups[state.group];
        }
    }

    QPID_LOG(debug, "Queue \"" << queue->getName() << "\": message group state replicated, key =" << groupIdHeader)
}
