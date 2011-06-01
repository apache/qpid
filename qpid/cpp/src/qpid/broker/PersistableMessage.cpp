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


#include "qpid/broker/PersistableMessage.h"
#include "qpid/broker/MessageStore.h"
#include <iostream>

using namespace qpid::broker;

namespace qpid {
namespace broker {

class MessageStore;

PersistableMessage::~PersistableMessage() {}

PersistableMessage::PersistableMessage() :
    store(0)
{}

void PersistableMessage::flush()
{
    syncList copy;
    {
        sys::ScopedLock<sys::Mutex> l(storeLock);
        if (store) {
            copy = synclist;
        } else {
            return;//early exit as nothing to do
        }
    }
    for (syncList::iterator i = copy.begin(); i != copy.end(); ++i) {
        PersistableQueue::shared_ptr q(i->second.lock());
        if (q) {
            q->flush();
        }
    }
}

void PersistableMessage::setContentReleased()
{
    contentReleaseState.released = true;
}

bool PersistableMessage::isContentReleased() const
{ 
    return contentReleaseState.released;
}
       

bool PersistableMessage::isStoredOnQueue(PersistableQueue::shared_ptr queue){
    if (store && (queue->getPersistenceId()!=0)) {
        sys::ScopedLock<sys::Mutex> l(storeLock);
        for (syncList::iterator i = synclist.begin(); i != synclist.end(); ++i) {
            PersistableQueue::shared_ptr q(i->second.lock());
            if (q && q->getPersistenceId() == queue->getPersistenceId())  return true;
        } 
    }            
    return false;
}


void PersistableMessage::addToSyncList(PersistableQueue::shared_ptr queue, MessageStore* _store) { 
    if (_store){
        sys::ScopedLock<sys::Mutex> l(storeLock);
        store = _store;
        boost::weak_ptr<PersistableQueue> q(queue);
        synclist[queue->getName()] = q;
    }
}

void PersistableMessage::enqueueAsync(PersistableQueue::shared_ptr queue, MessageStore* _store) { 
    addToSyncList(queue, _store);
    enqueueStart();
}

void PersistableMessage::dequeueComplete(PersistableQueue::shared_ptr queue, MessageStore* _store)
{
    if (_store){
        sys::ScopedLock<sys::Mutex> l(storeLock);
        synclist.erase(queue->getName());
    }
}

PersistableMessage::ContentReleaseState::ContentReleaseState() : blocked(false), requested(false), released(false) {}

void PersistableMessage::setStore(MessageStore* s)
{
    store = s;
}

void PersistableMessage::requestContentRelease()
{
    contentReleaseState.requested = true;
}
void PersistableMessage::blockContentRelease()
{ 
    contentReleaseState.blocked = true;
}
bool PersistableMessage::checkContentReleasable()
{ 
    return contentReleaseState.requested && !contentReleaseState.blocked;
}

bool PersistableMessage::isContentReleaseBlocked()
{
    return contentReleaseState.blocked;
}

bool PersistableMessage::isContentReleaseRequested()
{
    return contentReleaseState.requested;
}

}}


