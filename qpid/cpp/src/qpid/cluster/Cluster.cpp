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
#include "Connection.h"

#include "qpid/broker/Broker.h"
#include "qpid/broker/SessionState.h"
#include "qpid/broker/Connection.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ClusterDumpRequestBody.h"
#include "qpid/framing/ClusterUpdateBody.h"
#include "qpid/framing/ClusterReadyBody.h"
#include "qpid/framing/ClusterConnectionDeliverCloseBody.h"
#include "qpid/framing/ClusterConnectionDeliverDoOutputBody.h"
#include "qpid/log/Statement.h"
#include "qpid/memory.h"
#include "qpid/shared_ptr.h"
#include "qmf/org/apache/qpid/cluster/Package.h"

#include <boost/bind.hpp>
#include <boost/cast.hpp>
#include <boost/current_function.hpp>
#include <algorithm>
#include <iterator>
#include <map>
#include <ostream>

namespace qpid {
namespace cluster {
using namespace qpid::framing;
using namespace qpid::sys;
using namespace std;
using namespace qpid::cluster;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
namespace _qmf = qmf::org::apache::qpid::cluster;

Cluster::Cluster(const std::string& name_, const Url& url_, broker::Broker& b) :
    broker(b),
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
    connectionEventQueue(EventQueue::forEach(boost::bind(&Cluster::connectionEvent, this, _1))),
    handler(&joiningHandler),
    joiningHandler(*this),
    memberHandler(*this)
{
    ManagementAgent* agent = ManagementAgent::Singleton::getInstance();
    if (agent != 0){
        _qmf::Package  packageInit(agent);
        mgmtObject = new _qmf::Cluster (agent, this, &broker,name.str(),url.str());
        agent->addObject (mgmtObject);
        mgmtObject->set_status("JOINING");
		
        // if first cluster up set new UUID to set_clusterID() else set UUID of cluster being joined.
    }
    QPID_LOG(notice, self << " joining cluster " << name.str());
    broker.addFinalizer(boost::bind(&Cluster::shutdown, this));
    cpgDispatchHandle.startWatch(poller);
    cpg.join(name);
}

Cluster::~Cluster() {}

void Cluster::insert(const boost::intrusive_ptr<Connection>& c) {
    Mutex::ScopedLock l(lock);
    connections.insert(ConnectionMap::value_type(ConnectionId(self, c.get()), c));
}

void Cluster::erase(ConnectionId id) {
    Mutex::ScopedLock l(lock);
    connections.erase(id);
}

void Cluster::leave() {
    QPID_LOG(notice, self << " leaving cluster " << name.str());
    cpg.leave(name);
    // Defer shut down to the final configChange when the group knows we've left.
}

void Cluster::mcastControl(const framing::AMQBody& body, Connection* cptr) {
    QPID_LOG(trace, "MCAST [" << self << "]: " << body);
    AMQFrame f(body);
    Event e(CONTROL, ConnectionId(self, cptr), f.size());
    Buffer buf(e);
    f.encode(buf);
    mcastEvent(e);
}

void Cluster::mcastBuffer(const char* data, size_t size, const ConnectionId& connection) {
    Event e(DATA, connection, size);
    memcpy(e.getData(), data, size);
    mcastEvent(e);
}

void Cluster::mcastEvent(const Event& e) {
    e.mcast(name, cpg);
}

size_t Cluster::size() const {
    Mutex::ScopedLock l(lock);
    return map.size();
}

std::vector<Url> Cluster::getUrls() const {
    Mutex::ScopedLock l(lock);
    return map.memberUrls();
} 

// FIXME aconway 2008-09-15: volatile for locked/unlocked functions.
// Check locking from Handler functions.
boost::intrusive_ptr<Connection> Cluster::getConnection(const ConnectionId& id) {
    Mutex::ScopedLock l(lock);
    if (id.getMember() == self)
        return boost::intrusive_ptr<Connection>(id.getConnectionPtr());
    ConnectionMap::iterator i = connections.find(id);
    if (i == connections.end()) { // New shadow connection.
        assert(id.getMember() != self);
        std::ostringstream mgmtId;
        mgmtId << name.str() << ":"  << id;
        ConnectionMap::value_type value(id, new Connection(*this, shadowOut, mgmtId.str(), id));
        i = connections.insert(value).first;
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
    try {
        MemberId from(nodeid, pid);
        Event e = Event::delivered(from, msg, msg_len);
        // Process cluster controls immediately 
        if (e.getConnectionId().getConnectionPtr() == 0)  { // Cluster control
            Buffer buf(e);
            AMQFrame frame;
            while (frame.decode(buf)) {
                QPID_LOG(trace, "DLVR [" << e.getConnectionId().getMember() << "]: " << *frame.getBody());
                if (!handler->invoke(e.getConnectionId().getMember(), frame))
                    throw Exception(QPID_MSG("Invalid cluster control"));
            }
        }
        else 
            handler->deliver(e);
    }
    catch (const std::exception& e) {
        QPID_LOG(critical, "Error in cluster deliver: " << e.what());
        leave();
    }
}

void Cluster::connectionEvent(const Event& e) {
    Buffer buf(e);
    assert(e.getConnection());
    if (e.getType() == DATA)
        e.getConnection()->deliverBuffer(buf);
    else {              // control
        AMQFrame frame;
        while (frame.decode(buf))
            e.getConnection()->received(frame);
    }
}

struct AddrList {
    const cpg_address* addrs;
    int count;
    const char* prefix;
    AddrList(const cpg_address* a, int n, const char* p=0) : addrs(a), count(n), prefix(p) {}
};

ostream& operator<<(ostream& o, const AddrList& a) {
    if (a.count && a.prefix) o << a.prefix;
    for (const cpg_address* p = a.addrs; p < a.addrs+a.count; ++p) {
        const char* reasonString;
        switch (p->reason) {
          case CPG_REASON_JOIN: reasonString =  " joined"; break;
          case CPG_REASON_LEAVE: reasonString =  " left";break;
          case CPG_REASON_NODEDOWN: reasonString =  " node-down";break;
          case CPG_REASON_NODEUP: reasonString =  " node-up";break;
          case CPG_REASON_PROCDOWN: reasonString =  " process-down";break;
          default: reasonString = " ";
        }
        qpid::cluster::MemberId member(*p);
        o << member << reasonString << ((p+1 < a.addrs+a.count) ? ", " : "");
    }
    return o;
}

void Cluster::dispatch(sys::DispatchHandle& h) {
    cpg.dispatchAll();
    h.rewatch();
}

void Cluster::disconnect(sys::DispatchHandle& ) {
    // FIXME aconway 2008-09-11: this should be logged as critical,
    // when we provide admin option to shut down cluster and let
    // members leave cleanly.
    stopClusterNode();
}

void Cluster::configChange(
    cpg_handle_t /*handle*/,
    cpg_name */*group*/,
    cpg_address *current, int nCurrent,
    cpg_address *left, int nLeft,
    cpg_address *joined, int nJoined)
{
    Mutex::ScopedLock l(lock);
    QPID_LOG(debug, "Cluster: " << AddrList(current, nCurrent) << ". "
             << AddrList(left, nLeft, "Left: "));
    
    if (find(left, left+nLeft, self) != left+nLeft) { 
        // I have left the group, this is the final config change.
        QPID_LOG(notice, self << " left cluster " << name.str());
        broker.shutdown();
        return;
    }
    
    map.left(left, nLeft);
    handler->configChange(current, nCurrent, left, nLeft, joined, nJoined);

    // FIXME aconway 2008-09-17: management update.
    //update mgnt stats
    updateMemberStats();
}

void Cluster::update(const MemberId& id, const framing::FieldTable& members, uint64_t dumper) {
    Mutex::ScopedLock l(lock);
    handler->update(id, members, dumper); 
}

void Cluster::dumpRequest(const MemberId& dumpee, const string& urlStr) {
    Mutex::ScopedLock l(lock);
    handler->dumpRequest(dumpee, urlStr);
}

void Cluster::ready(const MemberId& member, const std::string& url) {
    Mutex::ScopedLock l(lock);
    handler->ready(member, url);
    // FIXME aconway 2008-09-17: management update.
}

broker::Broker& Cluster::getBroker(){ return broker; }

void Cluster::stall() {
    Mutex::ScopedLock l(lock);
    // Stop processing connection events. We still process config changes
    // and cluster controls in deliver()
    connectionEventQueue.stop();

    // FIXME aconway 2008-09-11: Flow control, we should slow down or
    // stop reading from local connections while stalled to avoid an
    // unbounded queue.
    //     if (mgmtObject!=0)
    //         mgmtObject->set_status("STALLED");
}

void Cluster::ready() {
    // Called with lock held
    QPID_LOG(info, self << " ready at URL " << url);
    mcastControl(ClusterReadyBody(ProtocolVersion(), url.str()), 0);
    handler = &memberHandler;   // Member mode.
    connectionEventQueue.start(poller);
    //     if (mgmtObject!=0)
    //         mgmtObject->set_status("ACTIVE");
}

// Called from Broker::~Broker when broker is shut down.  At this
// point we know the poller has stopped so no poller callbacks will be
// invoked. We must ensure that CPG has also shut down so no CPG
// callbacks will be invoked.
// 
void Cluster::shutdown() {
    QPID_LOG(notice, self << " shutting down.");
    try { cpg.shutdown(); }
    catch (const std::exception& e) { QPID_LOG(error, "During CPG shutdown: " << e.what()); }
    delete this;
}

ManagementObject* Cluster::GetManagementObject(void) const {
    return (ManagementObject*) mgmtObject;
}

Manageable::status_t Cluster::ManagementMethod (uint32_t methodId, Args& /*args*/, string&) {
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;
    QPID_LOG (debug, "Queue::ManagementMethod [id=" << methodId << "]");

    switch (methodId)
    {
      case _qmf::Cluster::METHOD_STOPCLUSTERNODE:
        stopClusterNode();
        break;
      case _qmf::Cluster::METHOD_STOPFULLCLUSTER:
        stopFullCluster();
        break;
    }

    return status;
}    

void Cluster::stopClusterNode(void)
{
    // FIXME aconway 2008-09-18: 
    QPID_LOG(notice, self << " disconnected from cluster " << name.str());
    broker.shutdown();
}

void Cluster::stopFullCluster(void)
{
    // FIXME aconway 2008-09-17: TODO
}

void Cluster::updateMemberStats(void)
{
    //update mgnt stats
    // FIXME aconway 2008-09-18: 
//     if (mgmtObject!=0){
//         mgmtObject->set_clusterSize(size()); 
//         std::vector<Url> vectUrl = getUrls();
//         string urlstr;
//         for(std::vector<Url>::iterator iter = vectUrl.begin(); iter != vectUrl.end(); iter++ ) {
//             if (iter != vectUrl.begin()) urlstr += ";";
//             urlstr += iter->str();
//         }
//         mgmtObject->set_members(urlstr);
//     }

}



}} // namespace qpid::cluster
