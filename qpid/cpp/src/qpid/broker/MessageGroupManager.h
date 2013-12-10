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

#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/QueueObserver.h"
#include "qpid/broker/MessageDistributor.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/sys/unordered_map.h"

#include "boost/shared_ptr.hpp"
#include <deque>

namespace qpid {
namespace broker {

class QueueObserver;
struct QueueSettings;
class MessageDistributor;
class Messages;
class Consumer;

class MessageGroupManager : public QueueObserver, public MessageDistributor
{
    static std::string defaultGroupId;  // assigned if no group id header present

    const std::string groupIdHeader;    // msg header holding group identifier
    const unsigned int timestamp;       // mark messages with timestamp if set
    Messages& messages;                 // parent Queue's in memory message container
    const std::string qName;            // name of parent queue (for logs)

    struct GroupState {
        // note: update getState()/setState() when changing this object's state implementation

        // track which messages are in this group, and if they have been acquired
        struct MessageState {
            qpid::framing::SequenceNumber position;
            bool                    acquired;
            MessageState() : acquired(false) {}
            MessageState(const qpid::framing::SequenceNumber& p) : position(p), acquired(false) {}
            bool operator<(const MessageState& b) const { return position < b.position; }
        };
        typedef std::deque<MessageState> MessageFifo;

        std::string group;  // group identifier
        std::string owner;  // consumer with outstanding acquired messages
        uint32_t acquired;  // count of outstanding acquired messages
        MessageFifo members;   // msgs belonging to this group, in enqueue order

        GroupState() : acquired(0) {}
        bool owned() const {return !owner.empty();}
        MessageFifo::iterator findMsg(const qpid::framing::SequenceNumber &);
    };

    typedef sys::unordered_map<std::string, struct GroupState> GroupMap;
    typedef std::map<qpid::framing::SequenceNumber, struct GroupState *> GroupFifo;

    GroupMap messageGroups; // index: group name
    GroupFifo freeGroups;   // ordered by oldest free msg
    //Consumers consumers;    // index: consumer name

    GroupState& findGroup( const Message& m );
    unsigned long hits, misses; // for debug
    uint32_t lastMsg;
    std::string lastGroup;
    GroupState *cachedGroup;

    void unFree( const GroupState& state );
    void own( GroupState& state, const std::string& owner );
    void disown( GroupState& state );

 public:
    static const std::string qpidMessageGroupKey;
    static const std::string qpidSharedGroup;   // if specified, one group can be consumed by multiple receivers
    static const std::string qpidMessageGroupTimestamp;

    static QPID_BROKER_EXTERN void setDefaults(const std::string& groupId);
    static boost::shared_ptr<MessageGroupManager> create( const std::string& qName,
                                                          Messages& messages,
                                                          const QueueSettings& settings );

    MessageGroupManager(const std::string& header, const std::string& _qName,
                        Messages& container, unsigned int _timestamp=0 )
      : groupIdHeader( header ), timestamp(_timestamp), messages(container),
        qName(_qName),
        hits(0), misses(0),
        lastMsg(0), cachedGroup(0) {}
    virtual ~MessageGroupManager();

    // QueueObserver iface
    void enqueued( const Message& qm );
    void acquired( const Message& qm );
    void requeued( const Message& qm );
    void dequeued( const Message& qm );
    void consumerAdded( const Consumer& ) {};
    void consumerRemoved( const Consumer& ) {};

    // MessageDistributor iface
    bool acquire(const std::string& c, Message& );
    void query(qpid::types::Variant::Map&) const;

    bool match(const qpid::types::Variant::Map*, const Message&) const;
};

}}

#endif
