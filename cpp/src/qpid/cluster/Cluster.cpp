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
#include "ConnectionInterceptor.h"

#include "qpid/broker/Broker.h"
#include "qpid/broker/SessionState.h"
#include "qpid/broker/Connection.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ClusterNotifyBody.h"
#include "qpid/framing/ClusterConnectionCloseBody.h"
#include "qpid/log/Statement.h"
#include "qpid/memory.h"
#include "qpid/shared_ptr.h"
#include <boost/bind.hpp>
#include <boost/cast.hpp>
#include <algorithm>
#include <iterator>
#include <map>

namespace qpid {
namespace cluster {
using namespace qpid::framing;
using namespace qpid::sys;
using namespace std;
using broker::Connection;

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

Cluster::Cluster(const std::string& name_, const Url& url_, broker::Broker& b) :
    cpg(*this),
    broker(&b),
    name(name_),
    url(url_),
    self(cpg.self())
{
    broker->addFinalizer(boost::bind(&Cluster::leave, this));
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
    cpg.shutdown();
    dispatcher.join();
}

// local connection initializes plugins
void Cluster::initialize(broker::Connection& c) {
    bool isLocal = &c.getOutput() != &shadowOut;
    if (isLocal)
        new ConnectionInterceptor(c, *this);
}

void Cluster::leave() {
    if (!broker.get()) return;  // Already left
    QPID_LOG(info, QPID_MSG("Leaving cluster " << *this));
    // Must not be called in the dispatch thread.
    assert(Thread::current().id() != dispatcher.id());
    cpg.leave(name);
    // Wait till final config-change is delivered and broker is released.
    {
        Mutex::ScopedLock l(lock);
        while(broker.get()) lock.wait();
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

void Cluster::send(const AMQFrame& frame, ConnectionInterceptor* connection) {
    QPID_LOG(trace, "MCAST [" << connection << "] " << frame);
    // FIXME aconway 2008-07-03: More efficient buffer management.
    // Cache coded form of decoded frames for re-encoding?
    Buffer buf(buffer);
    assert(frame.size() + 64 < sizeof(buffer));
    frame.encode(buf);
    encodePtr(buf, connection);
    iovec iov = { buffer, buf.getPosition() };
    cpg.mcast(name, &iov, 1);
}

void Cluster::notify() {
    send(AMQFrame(in_place<ClusterNotifyBody>(ProtocolVersion(), url.str())), 0);
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

ConnectionInterceptor* Cluster::getShadowConnection(const Cpg::Id& member, void* remotePtr) {
    ShadowConnectionId id(member, remotePtr);
    ShadowConnectionMap::iterator i = shadowConnectionMap.find(id);
    if (i == shadowConnectionMap.end()) { // A new shadow connection.
        std::ostringstream os;
        os << name << ":"  << member << ":" << remotePtr;
        broker::Connection* c = new broker::Connection(&shadowOut, *broker, os.str());
        ShadowConnectionMap::value_type value(id, new ConnectionInterceptor(*c, *this, id));
        i = shadowConnectionMap.insert(value).first;
    }
    return i->second;
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
        ConnectionInterceptor* connection;
        decodePtr(buf, connection);
        QPID_LOG(trace, "DLVR [" << from << " " << connection << "] " << frame);

        if (!broker.get()) {
            QPID_LOG(warning, "Ignoring late DLVR, already left the cluster.");
            return;
        }

        if (connection && from != self) // Look up shadow for remote connections
            connection = getShadowConnection(from, connection);

        if (frame.getMethod() && frame.getMethod()->amqpClassId() == CLUSTER_CLASS_ID) 
            handleMethod(from, connection, *frame.getMethod());
        else 
            connection->deliver(frame);
    }
    catch (const std::exception& e) {
        // FIXME aconway 2008-01-30: exception handling.
        QPID_LOG(critical, "Error in cluster delivery: " << e.what());
        assert(0);
        throw;
    }
}

// Handle cluster methods
// FIXME aconway 2008-07-11: Generate/template a better dispatch mechanism.
void Cluster::handleMethod(Id from, ConnectionInterceptor* connection, AMQMethodBody& method) {
    assert(method.amqpClassId() == CLUSTER_CLASS_ID);
    switch (method.amqpMethodId()) {
      case CLUSTER_NOTIFY_METHOD_ID: {
          ClusterNotifyBody& notify=static_cast<ClusterNotifyBody&>(method);
          Mutex::ScopedLock l(lock);
          members[from].url=notify.getUrl();
          lock.notifyAll();
          break;
      }
      case CLUSTER_CONNECTION_CLOSE_METHOD_ID: {
          if (!connection->isLocal())
              shadowConnectionMap.erase(connection->getShadowId());
          connection->deliverClosed();
          break;
      }
      case CLUSTER_CONNECTION_DO_OUTPUT_METHOD_ID: {
          connection->deliverDoOutput();
          break;
      }
      default:
        assert(0);
    }
}

void Cluster::configChange(
    cpg_handle_t /*handle*/,
    cpg_name */*group*/,
    cpg_address *current, int nCurrent,
    cpg_address *left, int nLeft,
    cpg_address */*joined*/, int nJoined)
{
    Mutex::ScopedLock l(lock);
    for (int i = 0; i < nLeft; ++i)  
        members.erase(left[i]);
    for(int j = 0; j < nCurrent; ++j) 
        members[current[j]].id = current[j];
    QPID_LOG(debug, "Cluster members: " << nCurrent << " ("<< nLeft << " left, " << nJoined << " joined):"
             << members);
    assert(members.size() == size_t(nCurrent));
    if (members.find(self) == members.end()) {
        QPID_LOG(debug, "Left cluster " << *this);
        broker = 0;             // Release broker reference.
    }

    lock.notifyAll();     // Threads waiting for membership changes.  
}

void Cluster::run() {
    cpg.dispatchBlocking();
}

}} // namespace qpid::cluster



