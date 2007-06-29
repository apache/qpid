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
    return out << cluster.name.str() << "(" << cluster.self << ")";
}

namespace {
Cluster::Member::Status statusMap[CPG_REASON_PROCDOWN+1];
struct StatusMapInit {
    StatusMapInit() {
    	statusMap[CPG_REASON_JOIN] = Cluster::Member::JOIN;
	statusMap[CPG_REASON_LEAVE] = Cluster::Member::LEAVE;
	statusMap[CPG_REASON_NODEDOWN] = Cluster::Member::NODEDOWN;
	statusMap[CPG_REASON_NODEUP] = Cluster::Member::NODEUP;
	statusMap[CPG_REASON_PROCDOWN] = Cluster::Member::PROCDOWN;
    }
} instance;
}

Cluster::Member::Member(const cpg_address& addr)
    : status(statusMap[addr.reason]) {}

void Cluster::notify() {
    ProtocolVersion version;     
    // TODO aconway 2007-06-25: Use proxy here.
    AMQFrame frame(version, 0,
                   make_shared_ptr(new ClusterNotifyBody(version, url)));
    handle(frame);
}

Cluster::Cluster(const std::string& name_, const std::string& url_) :
    name(name_),
    url(url_), 
    cpg(new Cpg(
            boost::bind(&Cluster::cpgDeliver, this, _1, _2, _3, _4, _5, _6),
            boost::bind(&Cluster::cpgConfigChange, this, _1, _2, _3, _4, _5, _6, _7, _8))),
    self(cpg->getLocalNoideId(), getpid())
{}

void Cluster::join(FrameHandler::Chain next) {
    QPID_LOG(trace, *this << " Joining cluster.");
    next = next;
    dispatcher=Thread(*this);
    cpg->join(name);
    notify();
}

Cluster::~Cluster() {
    if (cpg) {
        try {
            QPID_LOG(trace, *this << " Leaving cluster.");
            cpg->leave(name);
            cpg.reset();
            dispatcher.join();
        } catch (const std::exception& e) {
            QPID_LOG(error, "Exception leaving cluster " << e.what());
        }
    }
}

void Cluster::handle(AMQFrame& frame) {
    assert(cpg);
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
    ClusterNotifyBody* notifyIn=
        dynamic_cast<ClusterNotifyBody*>(frame.getBody().get());
    if (notifyIn) {
        {
            Mutex::ScopedLock l(lock);
            assert(members[from]);
            members[from]->url = notifyIn->getUrl();
            members[from]->status = Member::BROKER;
        }
        if (callback) 
            callback();
    }
    else 
        next->handle(frame);
}

void Cluster::cpgConfigChange(
    cpg_handle_t /*handle*/,
    struct cpg_name */*group*/,
    struct cpg_address *current, int nCurrent,
    struct cpg_address *left, int nLeft,
    struct cpg_address *joined, int nJoined
)
{
    QPID_LOG(trace,
             *this << " Configuration change. " << endl
             << "  Joined: " << make_pair(joined, nJoined) << endl
             << "  Left: " << make_pair(left, nLeft) << endl
             << "  Current: " << make_pair(current, nCurrent));

    bool needNotify=false;
    MemberList updated;
    {
        Mutex::ScopedLock l(lock);
        for (int i = 0; i < nJoined; ++i) {
            Id id(current[i]);
            members[id].reset(new Member(current[i]));
            if (id != self)
                needNotify = true; // Notify new members other than myself.
        }
        for (int i = 0; i < nLeft; ++i) 
            members.erase(Id(current[i]));
    } // End of locked scope.
    if (needNotify)
        notify();
    if (callback)
        callback();
}

void Cluster::setCallback(boost::function<void()> f) { callback=f; }

void Cluster::run() {
    assert(cpg);
    cpg->dispatchBlocking();
}

}} // namespace qpid::cluster



