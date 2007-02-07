/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <boost/bind.hpp>
#include "Reference.h"
#include "BrokerMessageMessage.h"
#include "QpidError.h"
#include "CompletionHandler.h"

namespace qpid {
namespace broker {

Reference&  ReferenceRegistry::open(const Reference::Id& id) {
    ReferenceMap::iterator i = references.find(id);
    // TODO aconway 2007-02-05: should we throw Channel or Connection
    // exceptions here?
    if (i != references.end())
        THROW_QPID_ERROR(CLIENT_ERROR, "Attempt to re-open reference " +id);
    return references[id] = Reference(id, this);
}

Reference&  ReferenceRegistry::get(const Reference::Id& id) {
    ReferenceMap::iterator i = references.find(id);
    if (i == references.end()) 
        THROW_QPID_ERROR(
            CLIENT_ERROR, "Attempt to use non-existent reference "+id);
    return i->second;
}

void  Reference::close() {
    for_each(messages.begin(), messages.end(),
             boost::bind(&Reference::complete, this, _1));
    registry->references.erase(getId());
}

void Reference::complete(MessagePtr message) {
    message->setAppends(appends);
    registry->handler.complete(message);
}

}} // namespace qpid::broker
