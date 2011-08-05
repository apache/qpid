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
#include "qpid/broker/LegacyLVQ.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/QueuedMessage.h"

namespace qpid {
namespace broker {

LegacyLVQ::LegacyLVQ(const std::string& k, bool b, Broker* br) : MessageMap(k), noBrowse(b), broker(br) {}

void LegacyLVQ::setNoBrowse(bool b)
{ 
    noBrowse = b;
}

bool LegacyLVQ::remove(const framing::SequenceNumber& position, QueuedMessage& message)
{
    Ordering::iterator i = messages.find(position);
    if (i != messages.end() &&
        // @todo KAG: gsim? is a bug? message is a *return* value - we really shouldn't check ".payload" below:
        i->second.payload == message.payload) {
        message = i->second;
        erase(i);
        return true;
    } else {
        return false;
    }
}

bool LegacyLVQ::next(const framing::SequenceNumber& position, QueuedMessage& message)
{
    if (MessageMap::next(position, message)) {
        if (!noBrowse) index.erase(getKey(message));
        return true;
    } else {
        return false;
    }
}

bool LegacyLVQ::push(const QueuedMessage& added, QueuedMessage& removed)
{
    //Hack to disable LVQ behaviour on cluster update:
    if (broker && broker->isClusterUpdatee()) {
        messages[added.position] = added;
        return false;
    } else {
        return MessageMap::push(added, removed);
    }
}

const QueuedMessage& LegacyLVQ::replace(const QueuedMessage& original, const QueuedMessage& update)
{ 
    //add the new message into the original position of the replaced message
    Ordering::iterator i = messages.find(original.position);
    i->second = update;
    i->second.position = original.position;
    return i->second;
}

void LegacyLVQ::removeIf(Predicate p)
{
    //Note: This method is currently called periodically on the timer
    //thread to expire messages. In a clustered broker this means that
    //the purging does not occur on the cluster event dispatch thread
    //and consequently that is not totally ordered w.r.t other events
    //(including publication of messages). The cluster does ensure
    //that the actual expiration of messages (as distinct from the
    //removing of those expired messages from the queue) *is*
    //consistently ordered w.r.t. cluster events. This means that
    //delivery of messages is in general consistent across the cluster
    //inspite of any non-determinism in the triggering of a
    //purge. However at present purging a last value queue (of the
    //legacy sort) could potentially cause inconsistencies in the
    //cluster (as the order w.r.t publications can affect the order in
    //which messages appear in the queue). Consequently periodic
    //purging of an LVQ is not enabled if the broker is clustered
    //(expired messages will be removed on delivery and consolidated
    //by key as part of normal LVQ operation).

    //TODO: Is there a neater way to check whether broker is
    //clustered? Here we assume that if the clustered timer is the
    //same as the regular timer, we are not clustered:
    if (!broker || &(broker->getClusterTimer()) == &(broker->getTimer()))
        MessageMap::removeIf(p);
}

std::auto_ptr<Messages> LegacyLVQ::updateOrReplace(std::auto_ptr<Messages> current, 
                                                   const std::string& key, bool noBrowse, Broker* broker)
{
    LegacyLVQ* lvq = dynamic_cast<LegacyLVQ*>(current.get());
    if (lvq) { 
        lvq->setNoBrowse(noBrowse);
        return current;
    } else {
        return std::auto_ptr<Messages>(new LegacyLVQ(key, noBrowse, broker));
    }
}

}} // namespace qpid::broker
