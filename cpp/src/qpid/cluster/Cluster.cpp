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
    broker(&b),
    poller(b.getPoller()),
    cpg(*this),
    name(name_),
    url(url_),
    self(cpg.self()),
    cpgDispatchHandle(cpg,
                      boost::bind(&Cluster::dispatch, this, _1), // read
                      0,                                         // write
                      boost::bind(&Cluster::disconnect, this, _1) // disconnect
    ),
    deliverQueue(boost::bind(&Cluster::deliverQueueCb, this, _1, _2)),
    mcastQueue(boost::bind(&Cluster::mcastQueueCb, this, _1, _2))
{
    broker->addFinalizer(boost::bind(&Cluster::leave, this));
    QPID_LOG(trace, "Joining cluster: " << name_);
    cpg.join(name);
    notify();

    // FIXME aconway 2008-08-11: can we remove this loop?
    // Dispatch till we show up in the cluster map.
    while (empty()) 
        cpg.dispatchOne();

    // Start dispatching from the poller.
    cpgDispatchHandle.startWatch(poller);
    deliverQueue.start(poller);
    mcastQueue.start(poller);
}

Cluster::~Cluster() {
    for (ShadowConnectionMap::iterator i = shadowConnectionMap.begin();
         i != shadowConnectionMap.end();
         ++i)
    {
        i->second->dirtyClose(); 
    }
    std::for_each(localConnectionSet.begin(), localConnectionSet.end(), boost::bind(&ConnectionInterceptor::dirtyClose, _1));
}

// local connection initializes plugins
void Cluster::initialize(broker::Connection& c) {
    bool isLocal = &c.getOutput() != &shadowOut;
    if (isLocal)
        localConnectionSet.insert(new ConnectionInterceptor(c, *this));
}

void Cluster::leave() {
    Mutex::ScopedLock l(lock);
    if (!broker) return;                               // Already left.
    // At this point the poller has already been shut down so
    // no dispatches can occur thru the cpgDispatchHandle.
    // 
    // FIXME aconway 2008-08-11: assert this is the cae.
    
    QPID_LOG(debug, "Leaving cluster " << *this);
    cpg.leave(name);
    // broker= is set to 0 when the final config-change is delivered.
    while(broker) {
        Mutex::ScopedUnlock u(lock);
        cpg.dispatchAll();
    }
    cpg.shutdown();
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
    mcastQueue.push(Message(frame, self, connection));
}

void Cluster::mcastQueueCb(const MessageQueue::iterator& begin,
                           const MessageQueue::iterator& end)
{
    // Static is OK because there is only one cluster allowed per
    // process and only one thread in mcastQueueCb at a time.
    static char buffer[64*1024]; // FIXME aconway 2008-07-04: buffer management.
    MessageQueue::iterator i = begin;
    while (i != end) {
        Buffer buf(buffer, sizeof(buffer));
        while (i != end && buf.available() > i->frame.size() + sizeof(uint64_t)) {
            i->frame.encode(buf);
            encodePtr(buf, i->connection);
            ++i;
        }
        iovec iov = { buffer, buf.getPosition() };
        cpg.mcast(name, &iov, 1);
    }
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

// ################ HERE - leaking shadow connections.
// FIXME aconway 2008-08-11: revisit memory management for shadow
// connections, what if the Connection is closed other than via
// disconnect? Dangling pointer in shadow map. Use ptr_map for shadow
// map, add deleted state to ConnectionInterceptor? Interceptors need
// to know about map? Check how Connections can be deleted.

ConnectionInterceptor* Cluster::getShadowConnection(const Cpg::Id& member, void* remotePtr) {
    ShadowConnectionId id(member, remotePtr);
    ShadowConnectionMap::iterator i = shadowConnectionMap.find(id);
    if (i == shadowConnectionMap.end()) { // A new shadow connection.
        std::ostringstream os;
        os << name << ":"  << member << ":" << remotePtr;
        assert(broker);
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
        while (buf.available() > 0) {
            AMQFrame frame;
            if (!frame.decode(buf))  // Not enough data.
                throw Exception("Received incomplete cluster event.");
            void* connection;
            decodePtr(buf, connection);
            deliverQueue.push(Message(frame, from, connection));
        }
    }
    catch (const std::exception& e) {
        // FIXME aconway 2008-01-30: exception handling.
        QPID_LOG(critical, "Error in cluster deliver: " << e.what());
        assert(0);
        throw;
    }
}

void Cluster::deliverQueueCb(const MessageQueue::iterator& begin,
                             const MessageQueue::iterator& end)
{
    for (MessageQueue::iterator i = begin; i != end; ++i) {
        AMQFrame& frame(i->frame);
        Id from(i->from);
        ConnectionInterceptor* connection = reinterpret_cast<ConnectionInterceptor*>(i->connection);
        try {
            QPID_LOG(trace, "DLVR [" << from << " " << connection << "] " << frame);

            if (!broker) {
                QPID_LOG(warning, "Unexpected DLVR, already left the cluster.");
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
            QPID_LOG(critical, "Error in cluster deliverQueueCb: " << e.what());
            assert(0);
            throw;
        }
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
          else
              localConnectionSet.erase(connection);
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
    if (members.find(self) == members.end()) 
        broker = 0;       // We have left the group, this is the final config change.
    lock.notifyAll();     // Threads waiting for membership changes.  
}

void Cluster::dispatch(sys::DispatchHandle& h) {
    cpg.dispatchAll();
    h.rewatch();
}

void Cluster::disconnect(sys::DispatchHandle& h) {
    h.stopWatch();
    // FIXME aconway 2008-08-11: error handling if we are disconnected. 
    // Kill the broker?
    assert(0);
}

}} // namespace qpid::cluster



