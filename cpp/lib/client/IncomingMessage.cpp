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

#include "IncomingMessage.h"
#include "Exception.h"
#include "ClientMessage.h"
#include <boost/format.hpp>

namespace qpid {
namespace client {

using boost::format;
using sys::Mutex;

IncomingMessage::Destination::~Destination() {}

void IncomingMessage::openReference(const std::string& name) {
    Mutex::ScopedLock l(lock);
    if (references.find(name) != references.end())
        throw ChannelException(
            406,  format("Attempt to open existing reference %s.") % name);
    references[name];
    return;
}

void IncomingMessage::appendReference(
    const std::string& name, const std::string& data)
{
    Mutex::ScopedLock l(lock);
    getRefUnlocked(name).data += data;
}

Message& IncomingMessage::createMessage(
    const std::string& destination, const std::string& reference)
{
    Mutex::ScopedLock l(lock);
    getDestUnlocked(destination); // Verify destination.
    Reference& ref = getRefUnlocked(reference);
    ref.messages.resize(ref.messages.size() +1);
    ref.messages.back().setDestination(destination);
    return ref.messages.back();
}

void  IncomingMessage::closeReference(const std::string& name) {
    Reference refCopy;
    {
        Mutex::ScopedLock l(lock);
        refCopy = getRefUnlocked(name);
        references.erase(name);
    }
    for (std::vector<Message>::iterator i = refCopy.messages.begin();
         i != refCopy.messages.end();
         ++i)
    {
        i->setData(refCopy.data);
        // TODO aconway 2007-03-23: Thread safety,
        // can a destination be removed while we're doing this?
        getDestination(i->getDestination()).message(*i);
    }
}


void  IncomingMessage::addDestination(std::string name, Destination& dest) {
    Mutex::ScopedLock l(lock);
    DestinationMap::iterator i = destinations.find(name);
    if (i == destinations.end()) 
        destinations[name]=&dest;
    else if (i->second != &dest)
        throw ChannelException(
            404, format("Destination already exists: %s.") % name);
}

void  IncomingMessage::removeDestination(std::string name) {
    Mutex::ScopedLock l(lock);
    DestinationMap::iterator i = destinations.find(name);
    if (i == destinations.end())
        throw ChannelException(
            406, format("No such destination: %s.") % name);
    destinations.erase(i);
}

IncomingMessage::Destination&  IncomingMessage::getDestination(
    const std::string& name) {
    return getDestUnlocked(name);
}

IncomingMessage::Reference&  IncomingMessage::getReference(
    const std::string& name) {
    return getRefUnlocked(name);
}

IncomingMessage::Reference&  IncomingMessage::getRefUnlocked(
    const std::string& name) {
    Mutex::ScopedLock l(lock);
    ReferenceMap::iterator i = references.find(name);
    if (i == references.end())
        throw ChannelException(
            404,  format("No such reference: %s.") % name);
    return i->second;
}

IncomingMessage::Destination&  IncomingMessage::getDestUnlocked(
    const std::string& name) {
    Mutex::ScopedLock l(lock);
    DestinationMap::iterator i = destinations.find(name);
    if (i == destinations.end())
        throw ChannelException(
            404,  format("No such destination: %s.") % name);
    return *i->second;
}

}} // namespace qpid::client
