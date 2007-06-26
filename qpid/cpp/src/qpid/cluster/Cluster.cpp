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

#include "Cluster.h"
#include "Cpg.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ClusterNotifyBody.h"
#include "qpid/log/Statement.h"
#include <boost/bind.hpp>
#include <algorithm>
#include <iterator>

namespace qpid {
namespace cluster {
using namespace qpid::framing;
using namespace qpid::sys;
using namespace std;

ostream& operator <<(ostream& out, const Cluster& cluster) {
    return out << cluster.name.str() << "(" << cluster.self << ")";
}

void Cluster::notify() {
    // TODO aconway 2007-06-25: Use proxy here.
    AMQFrame frame(version, 0,
                   make_shared_ptr(new ClusterNotifyBody(version, url)));
    handle(frame);
}

Cluster::Cluster(
    const std::string& name_, const std::string& url_, FrameHandler& next_,
    ProtocolVersion ver)
    : name(name_), url(url_), version(ver),
      cpg(new Cpg(boost::bind(&Cluster::cpgDeliver, this, _1, _2, _3, _4, _5, _6),
                  boost::bind(&Cluster::cpgConfigChange, this, _1, _2, _3, _4, _5, _6, _7, _8))),
      next(next_)
{
    self=Id(cpg->getLocalNoideId(), getpid());
    QPID_LOG(trace, *this << " Joining cluster.");
    cpg->join(name);
    notify();
    dispatcher=Thread(*this);
}

Cluster::~Cluster() {
    try {
        QPID_LOG(trace, *this << " Leaving cluster.");
        cpg->leave(name);
        cpg.reset();
        dispatcher.join();
    } catch (const std::exception& e) {
        QPID_LOG(error, "Exception leaving cluster " << e.what());
    }
}

void Cluster::handle(AMQFrame& frame) {
    QPID_LOG(trace, *this << " SEND: " << frame);
    Buffer buf(frame.size());
    frame.encode(buf);
    buf.flip();
    iovec iov = { buf.start(), frame.size() };
    cpg->mcast(name, &iov, 1);
}

size_t Cluster::size() const {
    Mutex::ScopedLock l(lock);
    return members.size();
}

Cluster::MemberList Cluster::getMembers() const {
    Mutex::ScopedLock l(lock);
    MemberList result(members.size());
    std::transform(members.begin(), members.end(), result.begin(),
                   boost::bind(&MemberMap::value_type::second, _1));
    return result;        
}

void Cluster::cpgDeliver(
        cpg_handle_t /*handle*/,
        struct cpg_name* /* group */,
        uint32_t nodeid,
        uint32_t pid,
        void* msg,
        int msg_len)
{
    Id from(nodeid, pid);
    Buffer buf(static_cast<char*>(msg), msg_len);
    AMQFrame frame;
    frame.decode(buf);
    QPID_LOG(trace, *this << " RECV: " << frame);
    // TODO aconway 2007-06-20: use visitor pattern.
    ClusterNotifyBody* notifyIn= dynamic_cast<ClusterNotifyBody*>(frame.getBody().get());
    if (notifyIn) {
        Mutex::ScopedLock l(lock);
        members[from].reset(new Member(notifyIn->getUrl()));
        lock.notifyAll();
    }
    else 
        next.handle(frame);
}

void Cluster::cpgConfigChange(
    cpg_handle_t /*handle*/,
    struct cpg_name */*group*/,
    struct cpg_address *ccMembers, int nMembers,
    struct cpg_address *left, int nLeft,
    struct cpg_address *joined, int nJoined
)
{
    QPID_LOG(
        trace,
        *this << " Configuration change. " << endl
        << "  Joined: " << make_pair(joined, nJoined) << endl
        << "  Left: " << make_pair(left, nLeft) << endl
        << "  Current: " << make_pair(ccMembers, nMembers));

    {
        Mutex::ScopedLock l(lock);
        // Erase members that left.
        for (int i = 0; i < nLeft; ++i) 
            members.erase(Id(left[i]));
        lock.notifyAll();
    }

    // If there are new members (other than myself) then notify.
    for (int i=0; i< nJoined; ++i) {
        if (Id(joined[i]) != self) {
            notify();
            break;
        }
    }

    // Note: New members are be added to my map when cpgDeliver
    // gets a cluster.notify frame.
}

void Cluster::run() {
    cpg->dispatchBlocking();
}

}} // namespace qpid::cluster



