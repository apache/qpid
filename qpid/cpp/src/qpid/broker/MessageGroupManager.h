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
#include "qpid/broker/MessageDistributor.h"
#include "qpid/sys/unordered_map.h"

namespace qpid {
namespace broker {

class QueueObserver;
class MessageDistributor;

class MessageGroupManager : public StatefulQueueObserver, public MessageDistributor
{
    static std::string defaultGroupId;  // assigned if no group id header present

    const std::string groupIdHeader;    // msg header holding group identifier
    const unsigned int timestamp;       // mark messages with timestamp if set
    Messages& messages;                 // parent Queue's in memory message container
    const std::string qName;            // name of parent queue (for logs)

    class GroupState;

    /** track consumers subscribed to this queue */
    class ConsumerState {
        // note: update getState()/setState() when changing this object's state implementation
        bool zombie;        // cancelled, but still holding messages.
        std::string name;
        uint32_t ownedGroups;
        uint32_t pendingMsgs;

    public:
        ConsumerState() : zombie(false), ownedGroups(0), pendingMsgs(0) {}
        const std::string& getName() const {return name;}
        void setName(const std::string& n) {name = n;}
        uint32_t groupCount() const {return ownedGroups;}
        uint32_t remainingMsgs() const {return pendingMsgs;}

        void addGroup( const GroupState& g);
        void removeGroup( const GroupState& g );
        bool cancelled() const {return zombie;}
        void cancel() {zombie = true;}
        void uncancel() {zombie = false;}   // resubscribed on existing session
        void msgAvailable(const GroupState& g, const QueuedMessage& qm);
        void msgAcquired(const GroupState& g, const QueuedMessage& qm);
    };
    typedef sys::unordered_map<std::string, ConsumerState> ConsumerMap;
    ConsumerMap consumers;    // index: consumer name

    /** track all known groups */
    class GroupState {
        // note: update getState()/setState() when changing this object's state implementation
        typedef std::deque<framing::SequenceNumber> PositionFifo;

        std::string name;  // group identifier
        uint32_t acquired;  // count of outstanding acquired messages
        ConsumerState *owner;  // consumer with outstanding acquired messages
        PositionFifo members;   // msgs belonging to this group

    public:
        GroupState() : acquired(0), owner(0) {}
        const std::string& getName() const {return name;}
        void setName(const std::string& n) {name = n;}
        uint32_t acquiredMsgs() const {return acquired;}
        uint32_t totalMsgs() const {return members.size();}
        bool isFree() const {return owner == 0;}

        void setOwner( ConsumerState& consumer );
        ConsumerState *getOwner() const { return owner; }
        void resetOwner();
        const framing::SequenceNumber& nextMsg() const;
        void enqueueMsg(const QueuedMessage& msg);
        void acquireMsg(const QueuedMessage& msg);
        void requeueMsg(const QueuedMessage& msg);
        void dequeueMsg(const QueuedMessage& msg);
        // for clustering:
        void getPositions(framing::Array& pos) const;
        void setPositions(const framing::Array& pos);
        void setAcquired(uint32_t c) {acquired = c;}

    };
    typedef sys::unordered_map<std::string, GroupState> GroupMap;
    GroupMap messageGroups;   // index: group name
    // cache the last lookup
    uint hits;
    uint misses;
    uint32_t lastMsg;
    std::string lastGroup;
    GroupState *cachedGroup;

    /** store free (un-owned) groups by the position of the oldest index */
    class GroupFifo {
        // orders groups by their next available message (oldest first)
        typedef std::map<framing::SequenceNumber, const GroupState *> Fifo;
        Fifo fifo;

    public:
        GroupFifo() {}
        void addGroup(const GroupState& group);
        void removeGroup(const GroupState& group);
        size_t groupCount() const {return fifo.size();}
        const GroupState& nextGroup() const;
        void clear() {fifo.clear();}
    };
    GroupFifo freeGroups;

    GroupState& findGroup( const QueuedMessage& qm );
    void deleteGroup(GroupState& group);
    void disownGroup(GroupState& group);        /** release a group from a subscriber */

    static const std::string qpidMessageGroupKey;
    static const std::string qpidSharedGroup;   // if specified, one group can be consumed by multiple receivers
    static const std::string qpidMessageGroupTimestamp;

 public:

    static QPID_BROKER_EXTERN void setDefaults(const std::string& groupId);
    static boost::shared_ptr<MessageGroupManager> create( const std::string& qName,
                                                          Messages& messages,
                                                          const qpid::framing::FieldTable& settings );

    MessageGroupManager(const std::string& header, const std::string& _qName,
                        Messages& container, unsigned int _timestamp=0 )
      : StatefulQueueObserver(std::string("MessageGroupManager:") + header),
      groupIdHeader( header ), timestamp(_timestamp), messages(container), qName(_qName),
      hits(0), misses(0),
      lastMsg(0), cachedGroup(0) {}
    virtual ~MessageGroupManager();

    // QueueObserver iface
    void enqueued( const QueuedMessage& qm );
    void acquired( const QueuedMessage& qm );
    void requeued( const QueuedMessage& qm );
    void dequeued( const QueuedMessage& qm );
    void consumerAdded( const Consumer& );
    void consumerRemoved( const Consumer& );
    void getState(qpid::framing::FieldTable& state ) const;
    void setState(const qpid::framing::FieldTable&);

    // MessageDistributor iface
    bool nextConsumableMessage(Consumer::shared_ptr& c, QueuedMessage& next);
    bool allocate(const std::string& c, const QueuedMessage& qm);
    bool nextBrowsableMessage(Consumer::shared_ptr& c, QueuedMessage& next);
    void query(qpid::types::Variant::Map&) const;

    bool match(const qpid::types::Variant::Map*, const QueuedMessage&) const;
};

}}

#endif
