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
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ClusterNotifyBody.h"
#include "qpid/log/Statement.h"
#include <boost/bind.hpp>
#include <algorithm>
#include <iterator>
#include <map>

namespace qpid {
namespace cluster {
using namespace qpid::framing;
using namespace qpid::sys;
using namespace std;


ostream& operator <<(ostream& out, const Cluster& cluster) {
    return out << "cluster[" << cluster.name.str() << " " << cluster.self << "]";
}

ostream& operator<<(ostream& out, const Cluster::MemberMap::value_type& m) {
    return out << m.first << "=" << m.second.url;
}

ostream& operator <<(ostream& out, const Cluster::MemberMap& members) {
    ostream_iterator<Cluster::MemberMap::value_type> o(out, " ");
    copy(members.begin(), members.end(), o);
    return out;
}

Cluster::Cluster(const std::string& name_, const std::string& url_, broker::Broker& broker) :
    FrameHandler(&sessions), 
    cpg(*this),
    name(name_),
    url(url_), 
    self(Id::self(cpg)),
    sessions(broker, *this)
{
    QPID_LOG(trace, *this << " Joining cluster: " << name_);
    cpg.join(name);
    notify();
    dispatcher=Thread(*this);
    // Wait till we show up in the cluster map.
    {
        Mutex::ScopedLock l(lock);
        while (empty())
            lock.wait();
    }
}

Cluster::~Cluster() {
    QPID_LOG(trace, *this << " Leaving cluster.");
    try {
        cpg.leave(name);
        dispatcher.join();
    }
    catch (const std::exception& e) {
        QPID_LOG(error, "Exception leaving cluster " << *this << ": "
                 << e.what());
    }
}

void Cluster::handle(AMQFrame& frame) {
    QPID_LOG(trace, *this << " SEND: " << frame);
    Buffer buf(frame.size());
    frame.encode(buf);
    buf.flip();
    iovec iov = { buf.start(), frame.size() };
    cpg.mcast(name, &iov, 1);
}

void Cluster::notify() {
    AMQFrame frame(in_place<ClusterNotifyBody>(ProtocolVersion(), url));
    handle(frame);
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

void Cluster::deliver(
    cpg_handle_t /*handle*/,
    cpg_name* /*group*/,
    uint32_t nodeid,
    uint32_t pid,
    void* msg,
    int msg_len)
{
    Id from(nodeid, pid);
    Buffer buf(static_cast<char*>(msg), msg_len);
    AMQFrame frame;
    frame.decode(buf);
    QPID_LOG(trace, *this << " RECV: " << frame << " from: " << from);
    if (frame.getChannel() == 0)
        handleClusterFrame(from, frame);
    else
        next->handle(frame);
}

bool Cluster::wait(boost::function<bool(const Cluster&)> predicate,
                   Duration timeout) const
{
    AbsTime deadline(now(), timeout);
    Mutex::ScopedLock l(lock);
    while (!predicate(*this) && lock.wait(deadline))
        ;
    return (predicate(*this));
}

// Handle cluster control frame from the null session.
void Cluster::handleClusterFrame(Id from, AMQFrame& frame) {
    // TODO aconway 2007-06-20: use visitor pattern here.
    ClusterNotifyBody* notifyIn=
        dynamic_cast<ClusterNotifyBody*>(frame.getBody());
    assert(notifyIn);
    MemberList list;
    {
        Mutex::ScopedLock l(lock);
        members[from].url=notifyIn->getUrl();
        lock.notifyAll();
        QPID_LOG(trace, *this << ": members joined: " << members);
    }
}

void Cluster::configChange(
    cpg_handle_t /*handle*/,
    cpg_name */*group*/,
    cpg_address */*current*/, int /*nCurrent*/,
    cpg_address *left, int nLeft,
    cpg_address *joined, int nJoined)
{
    bool newMembers=false;
    MemberList updated;
    {
        Mutex::ScopedLock l(lock);
        if (nLeft) {
            for (int i = 0; i < nLeft; ++i) 
                members.erase(Id(left[i]));
            QPID_LOG(trace, *this << ": members left: " << members);
            lock.notifyAll();
        }
        newMembers = nJoined > 1 || (nJoined==1 && Id(joined[0]) != self);
        // We don't record members joining here, we record them when
        // we get their ClusterNotify message.
    }
    if (newMembers)             // Notify new members of my presence.
        notify();
}

void Cluster::run() {
    cpg.dispatchBlocking();
}

}} // namespace qpid::cluster



