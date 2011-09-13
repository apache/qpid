#ifndef _broker_MessageGroupManager_h
#define _broker_MessageGroupManager_h

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

/* for managing message grouping on Queues */

#include "qpid/broker/StatefulQueueObserver.h"
#include "qpid/broker/MessageAllocator.h"


namespace qpid {
namespace broker {

class QueueObserver;
class MessageAllocator;

class MessageGroupManager : public StatefulQueueObserver, public MessageAllocator
{
    const std::string groupIdHeader;    // msg header holding group identifier
    const unsigned int timestamp;       // mark messages with timestamp if set

    struct GroupState {
        typedef std::list<framing::SequenceNumber> PositionFifo;

        std::string group;  // group identifier
        std::string owner;  // consumer with outstanding acquired messages
        uint32_t acquired;  // count of outstanding acquired messages
        //uint32_t total;     // count of enqueued messages in this group
        PositionFifo members;   // msgs belonging to this group

        GroupState() : acquired(0) {}
        bool owned() const {return !owner.empty();}
    };
    typedef std::map<std::string, struct GroupState> GroupMap;
    typedef std::map<std::string, uint32_t> Consumers;  // count of owned groups
    typedef std::map<framing::SequenceNumber, struct GroupState *> GroupFifo;

    // note: update getState()/setState() when changing this object's state implementation
    GroupMap messageGroups; // index: group name
    GroupFifo freeGroups;   // ordered by oldest free msg
    Consumers consumers;    // index: consumer name

    static const std::string qpidMessageGroupKey;
    static const std::string qpidMessageGroupTimestamp;
    static const std::string qpidMessageGroupDefault;

    const std::string getGroupId( const QueuedMessage& qm ) const;
    void unFree( const GroupState& state )
    {
        GroupFifo::iterator pos = freeGroups.find( state.members.front() );
        assert( pos != freeGroups.end() && pos->second == &state );
        freeGroups.erase( pos );
    }
    void own( GroupState& state, const std::string& owner )
    {
        state.owner = owner;
        consumers[state.owner]++;
        unFree( state );
    }
    void disown( GroupState& state )
    {
        assert(consumers[state.owner]);
        consumers[state.owner]--;
        state.owner.clear();
        assert(state.members.size());
#ifdef NDEBUG
        freeGroups[state.members.front()] = &state;
#else
        bool unique = freeGroups.insert(GroupFifo::value_type(state.members.front(), &state)).second;
        (void) unique; assert(unique);
#endif
    }

 public:

    static boost::shared_ptr<MessageGroupManager> create( Queue *q, const qpid::framing::FieldTable& settings );

    MessageGroupManager(const std::string& header, Queue *q, unsigned int _timestamp=0 )
      : StatefulQueueObserver(std::string("MessageGroupManager:") + header), MessageAllocator(q),
        groupIdHeader( header ), timestamp(_timestamp) {}
    void enqueued( const QueuedMessage& qm );
    void acquired( const QueuedMessage& qm );
    void requeued( const QueuedMessage& qm );
    void dequeued( const QueuedMessage& qm );
    void consumerAdded( const Consumer& );
    void consumerRemoved( const Consumer& );
    void getState(qpid::framing::FieldTable& state ) const;
    void setState(const qpid::framing::FieldTable&);

    bool nextConsumableMessage( Consumer::shared_ptr& c, QueuedMessage& next,
                                const sys::Mutex::ScopedLock&);
    // uses default nextBrowsableMessage()
    bool acquirable(const std::string& consumer, const QueuedMessage& msg,
                    const sys::Mutex::ScopedLock&);
    void query(qpid::types::Variant::Map&, const sys::Mutex::ScopedLock&) const;
    bool match(const qpid::types::Variant::Map*, const QueuedMessage&) const;
};

}}

#endif
