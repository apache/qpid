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
#include "ErrorCheck.h"
#include "EventFrame.h"
#include "ClusterMap.h"
#include "Cluster.h"
#include "qpid/framing/ClusterErrorCheckBody.h"
#include "qpid/framing/ClusterConfigChangeBody.h"
#include "qpid/log/Statement.h"

#include <algorithm>

namespace qpid {
namespace cluster {

using namespace std;
using namespace framing;
using namespace framing::cluster;

ErrorCheck::ErrorCheck(Cluster& c)
    : cluster(c), mcast(c.getMulticast()), frameSeq(0), type(ERROR_TYPE_NONE), connection(0)
{}

ostream& operator<<(ostream& o, ErrorCheck::MemberSet ms) {
    copy(ms.begin(), ms.end(), ostream_iterator<MemberId>(o, " "));
    return o;
}

void ErrorCheck::error(Connection& c, ErrorType t, uint64_t seq, const MemberSet& ms)
{
    // Detected a local error, inform cluster and set error state.
    assert(t != ERROR_TYPE_NONE); // Must be an error.
    assert(type == ERROR_TYPE_NONE); // Can only be called while processing
    type = t;
    unresolved = ms;
    frameSeq = seq;
    connection = &c;
    QPID_LOG(debug, cluster << (type == ERROR_TYPE_SESSION ? " Session" : " Connection")
             << " error " << frameSeq << " unresolved: " << unresolved);
    mcast.mcastControl(ClusterErrorCheckBody(ProtocolVersion(), type, frameSeq), cluster.getId());
}

void ErrorCheck::delivered(const EventFrame& e) {
    if (isUnresolved()) {
        const ClusterErrorCheckBody* errorCheck =
            dynamic_cast<const ClusterErrorCheckBody*>(e.frame.getMethod());
        const ClusterConfigChangeBody* configChange =
            dynamic_cast<const ClusterConfigChangeBody*>(e.frame.getMethod());

        if (errorCheck && errorCheck->getFrameSeq() == frameSeq) { // Same error
            if (errorCheck->getType() < type) { // my error is worse than his
                QPID_LOG(critical, cluster << " Error " << frameSeq << " did not occur on " << e.getMemberId());
                throw Exception("Aborted by local failure that did not occur on all replicas");
            }
            else {              // his error is worse/same as mine.
                QPID_LOG(critical, cluster << " Error " << frameSeq << " outcome agrees with " << e.getMemberId());
                unresolved.erase(e.getMemberId());
                checkResolved();
            }
        }
        else {
            frames.push_back(e); // Only drop matching errorCheck controls.
            if (configChange) {
                MemberSet members(ClusterMap::decode(configChange->getCurrent()));
                MemberSet result;
                set_intersection(members.begin(), members.end(),
                                 unresolved.begin(), unresolved.end(),
                                 inserter(result, result.begin()));
                unresolved.swap(result);
                checkResolved();
            }
        }
    }
    else 
        frames.push_back(e);
}

void ErrorCheck::checkResolved() {
    if (unresolved.empty()) {   // No more potentially conflicted members, we're clear.
        type = ERROR_TYPE_NONE;
        QPID_LOG(debug, cluster << " Error " << frameSeq << " resolved.");
    }
    else 
        QPID_LOG(debug, cluster << " Error " << frameSeq << " still unresolved: " << unresolved);
}

EventFrame ErrorCheck::getNext() {
    assert(canProcess());
    EventFrame e(frames.front());
    frames.pop_front();
    return e;
}

bool ErrorCheck::canProcess() const {
    return type == ERROR_TYPE_NONE && !frames.empty();
}

bool ErrorCheck::isUnresolved() const {
    return type != ERROR_TYPE_NONE;
}
    
}} // namespace qpid::cluster
