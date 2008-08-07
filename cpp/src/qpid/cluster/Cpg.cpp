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

#include "Cpg.h"

#include "qpid/sys/Mutex.h"
#include "qpid/log/Statement.h"

#include <vector>
#include <limits>
#include <iterator>

#include <unistd.h>

namespace qpid {
namespace cluster {

using namespace std;

Cpg* Cpg::cpgFromHandle(cpg_handle_t handle) {
    void* cpg=0;
    check(cpg_context_get(handle, &cpg), "Cannot get CPG instance.");
    if (!cpg) throw Exception("Cannot get CPG instance.");
    return reinterpret_cast<Cpg*>(cpg);
}

// Global callback functions.
void Cpg::globalDeliver (
    cpg_handle_t handle,
    struct cpg_name *group,
    uint32_t nodeid,
    uint32_t pid,
    void* msg,
    int msg_len)
{
    cpgFromHandle(handle)->handler.deliver(handle, group, nodeid, pid, msg, msg_len);
}

void Cpg::globalConfigChange(
    cpg_handle_t handle,
    struct cpg_name *group,
    struct cpg_address *members, int nMembers,
    struct cpg_address *left, int nLeft,
    struct cpg_address *joined, int nJoined
)
{
    cpgFromHandle(handle)->handler.configChange(handle, group, members, nMembers, left, nLeft, joined, nJoined);
}

Cpg::Cpg(Handler& h) : handler(h), isShutdown(false) {
    cpg_callbacks_t callbacks = { &globalDeliver, &globalConfigChange };
    check(cpg_initialize(&handle, &callbacks), "Cannot initialize CPG");
    check(cpg_context_set(handle, this), "Cannot set CPG context");
    QPID_LOG(debug, "Initialize CPG handle 0x" << std::hex << handle);
}

Cpg::~Cpg() {
    try {
        shutdown();
    } catch (const std::exception& e) {
        QPID_LOG(error, "Exception in Cpg destructor: " << e.what());
    }
}

void Cpg::join(const Name& group) {
    check(cpg_join(handle, const_cast<Name*>(&group)),cantJoinMsg(group));
}
    
void Cpg::leave(const Name& group) {
    check(cpg_leave(handle,const_cast<Name*>(&group)),cantLeaveMsg(group));
}

bool Cpg::isFlowControlEnabled() {
    cpg_flow_control_state_t flowState;
    check(cpg_flow_control_state_get(handle, &flowState), "Cannot get CPG flow control status.");
    return flowState == CPG_FLOW_CONTROL_ENABLED;
}

// TODO aconway 2008-08-07: better handling of flow control.
// Wait for flow control to be disabled.
void Cpg::waitForFlowControl() {
    int delayNs=1000;           // one millisecond
    int tries=8;                // double the delay on each try.
    while (isFlowControlEnabled() && tries > 0) {
        QPID_LOG(warning, "CPG flow control enabled, retry in " << delayNs << "ns");
        ::usleep(delayNs);
        --tries;
        delayNs *= 2;
    };
    if (tries == 0) {
        // FIXME aconway 2008-08-07: this is a fatal leave-the-cluster condition.
        throw Cpg::Exception("CPG flow control enabled, failed to send.");
    }
}

void Cpg::mcast(const Name& group, const iovec* iov, int iovLen) {
    waitForFlowControl();
    cpg_error_t result;
    do {
        result = cpg_mcast_joined(handle, CPG_TYPE_AGREED, const_cast<iovec*>(iov), iovLen);
        if (result != CPG_ERR_TRY_AGAIN) check(result, cantMcastMsg(group));
    } while(result == CPG_ERR_TRY_AGAIN);
}

void Cpg::shutdown() {
    if (!isShutdown) {
        QPID_LOG(debug,"Shutting down CPG");
        isShutdown=true;
        check(cpg_finalize(handle), "Error in shutdown of CPG");
    }
}

string Cpg::errorStr(cpg_error_t err, const std::string& msg) {
    switch (err) {
      case CPG_OK: return msg+": ok";
      case CPG_ERR_LIBRARY: return msg+": library";
      case CPG_ERR_TIMEOUT: return msg+": timeout";
      case CPG_ERR_TRY_AGAIN: return msg+": timeout. The aisexec daemon may not be running";
      case CPG_ERR_INVALID_PARAM: return msg+": invalid param";
      case CPG_ERR_NO_MEMORY: return msg+": no memory";
      case CPG_ERR_BAD_HANDLE: return msg+": bad handle";
      case CPG_ERR_ACCESS: return msg+": access denied. You may need to set your group ID to 'ais'";
      case CPG_ERR_NOT_EXIST: return msg+": not exist";
      case CPG_ERR_EXIST: return msg+": exist";
      case CPG_ERR_NOT_SUPPORTED: return msg+": not supported";
      case CPG_ERR_SECURITY: return msg+": security";
      case CPG_ERR_TOO_MANY_GROUPS: return msg+": too many groups";
      default:
        assert(0);
        return ": unknown";
    };
}

std::string Cpg::cantJoinMsg(const Name& group) {
    return "Cannot join CPG group "+group.str();
}

std::string Cpg::cantLeaveMsg(const Name& group) {
    return "Cannot leave CPG group "+group.str();
}

std::string Cpg::cantMcastMsg(const Name& group) {
    return "Cannot mcast to CPG group "+group.str();
}

Cpg::Id Cpg::self() const {
    unsigned int nodeid;
    check(cpg_local_get(handle, &nodeid), "Cannot get local CPG identity");
    return Id(nodeid, getpid());
}

ostream& operator<<(ostream& o, std::pair<cpg_address*,int> a) {
    ostream_iterator<Cpg::Id> i(o, " ");
    std::copy(a.first, a.first+a.second, i);
    return o;
}

ostream& operator <<(ostream& out, const Cpg::Id& id) {
    return out << id.getNodeId() << "-" << id.getPid();
}

ostream& operator <<(ostream& out, const cpg_name& name) {
    return out << string(name.value, name.length);
}


}} // namespace qpid::cluster



