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
#include "qpid/broker/Broker.h"
#include "qpid/broker/SessionState.h"
#include "qpid/broker/Connection.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ClusterNotifyBody.h"
#include "qpid/log/Statement.h"
#include "qpid/memory.h"
#include <boost/bind.hpp>
#include <boost/scoped_array.hpp>
#include <algorithm>
#include <iterator>
#include <map>

namespace qpid {
namespace cluster {
using namespace qpid::framing;
using namespace qpid::sys;
using namespace std;
using broker::Connection;

// Beginning of inbound chain: send to cluster.
struct ClusterSendHandler : public HandlerChain<FrameHandler>::Handler {
    Cluster::ConnectionChain& connection;
    Cluster& cluster;
    
    ClusterSendHandler(Cluster::ConnectionChain& conn, Cluster& clust) : connection(conn), cluster(clust) {}

    void handle(AMQFrame& f) {
        // FIXME aconway 2008-01-29: Refcount Connections to ensure
        // Connection not destroyed till message is self delivered.
        cluster.send(f, &connection, next); // Indirectly send to next via cluster.
    }
};

void Cluster::initialize(Cluster::ConnectionChain& cc) {
    cc.push(ConnectionChain::HandlerAutoPtr(new ClusterSendHandler(cc, *this)));
}

ostream& operator <<(ostream& out, const Cluster& cluster) {
    return out << cluster.name.str() << "-" << cluster.self;
}

ostream& operator<<(ostream& out, const Cluster::MemberMap::value_type& m) {
    return out << m.first << "=" << m.second.url;
}

ostream& operator <<(ostream& out, const Cluster::MemberMap& members) {
    ostream_iterator<Cluster::MemberMap::value_type> o(out, " ");
    copy(members.begin(), members.end(), o);
    return out;
}

// FIXME aconway 2008-07-02: create a Connection for the cluster.
Cluster::Cluster(const std::string& name_, const Url& url_, broker::Broker& b) :
    broker(b),
    cpg(*this),
    name(name_),
    url(url_),
    self(cpg.self())
{
    QPID_LOG(trace, "Joining cluster: " << name_);
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
        cpg.shutdown();
        dispatcher.join();
    }
    catch (const std::exception& e) {
        QPID_LOG(error, "Exception leaving cluster " << *this << ": "
                 << e.what());
    }
}

template <class T> void decodePtr(Buffer& buf, T*& ptr) {
    uint64_t value = buf.getLongLong();
    ptr = reinterpret_cast<T*>(value);
}

template <class T> void encodePtr(Buffer& buf, T* ptr) {
    uint64_t value = reinterpret_cast<uint64_t>(ptr);
    buf.putLongLong(value);
}

void Cluster::send(AMQFrame& frame, void* connection, FrameHandler* next) {
    QPID_LOG(trace, "MCAST [" << connection << "] " << frame);
    // TODO aconway 2008-07-03: More efficient buffer management.
    // Cache coded form of decoded frames for re-encoding?
    Buffer buf(buffer);
    assert(frame.size() + 128 < sizeof(buffer));
    frame.encode(buf);
    encodePtr(buf, connection);
    encodePtr(buf, next);
    iovec iov = { buffer, buf.getPosition() };
    cpg.mcast(name, &iov, 1);
}

void Cluster::notify() {
    AMQFrame frame(in_place<ClusterNotifyBody>(ProtocolVersion(), url.str()));
    send(frame, 0, 0);
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

boost::shared_ptr<broker::Connection>
Cluster::getShadowConnection(const Cpg::Id& member, void* connectionPtr) {
    // FIXME aconway 2008-07-02: locking - called by deliver in
    // cluster thread so no locks but may need to revisit as model
    // changes.
    ShadowConnectionId id(member, connectionPtr);
    boost::shared_ptr<broker::Connection>& ptr = shadowConnectionMap[id];
    if (!ptr) {
        std::ostringstream os;
        os << name << ":"  << member << ":" << std::hex << connectionPtr;
        ptr.reset(new broker::Connection(&shadowOut, broker, os.str()));
    }
    return ptr;
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
    try {
        Buffer buf(static_cast<char*>(msg), msg_len);
        AMQFrame frame;
        frame.decode(buf);
        void* connectionId;
        decodePtr(buf, connectionId);

        QPID_LOG(trace, "DLVR [" << from << " " << connectionId << "] " << frame);

        if (connectionId == 0) // A cluster control frame.
            handleClusterFrame(from, frame);
        else if (from == self) { // My own frame, carries a next pointer.
            FrameHandler* next; 
            decodePtr(buf, next);
            next->handle(frame);
        }
        else {                  // Foreign frame, forward to shadow connection.
            // FIXME aconway 2008-07-02: ptr_map instead of shared_ptr.
            boost::shared_ptr<broker::Connection> shadow = getShadowConnection(from, connectionId);
            shadow->received(frame);
        }
    }
    catch (const std::exception& e) {
        // FIXME aconway 2008-01-30: exception handling.
        QPID_LOG(error, "Error handling frame from cluster " << e.what());
    }
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

// Handle cluster control frame .
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
        QPID_LOG(debug, "Cluster join: " << members);
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
            QPID_LOG(debug, "Cluster leave: " << members);
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



