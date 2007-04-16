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

#include "Correlator.h"

namespace qpid {
namespace framing {

void Correlator::request(RequestId id, Action action) {
    actions[id] = action;
}

bool Correlator::response(shared_ptr<AMQResponseBody> r) {
    Actions::iterator begin = actions.lower_bound(r->getRequestId());
    RequestId last = r->getRequestId()+r->getBatchOffset();
    Actions::iterator i = begin;
    bool didAction = false;
    for( ;  i != actions.end() && i->first <= last; ++i) {
        didAction = true;
        // FIXME aconway 2007-04-04: handle exceptions thrown by action.
        i->second(r);
    }
    actions.erase(begin, i);
    return didAction;
}


}} // namespace qpid::framing
