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
#include "qpid/sys/Time.h"
#include "qpid/sys/posix/PrivatePosix.h"
#include "qpid/log/Statement.h"

#include <vector>
#include <limits>
#include <iterator>
#include <sstream>

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

int Cpg::getFd() {
    int fd;
    check(cpg_fd_get(handle, &fd), "Cannot get CPG file descriptor");
    return fd;
}

Cpg::Cpg(Handler& h) : IOHandle(new sys::IOHandlePrivate), handler(h), isShutdown(false) {
    cpg_callbacks_t callbacks;
    ::memset(&callbacks, sizeof(callbacks), 0);
    callbacks.cpg_deliver_fn = &globalDeliver;
    callbacks.cpg_confchg_fn = &globalConfigChange;
    cpg_error_t err = cpg_initialize(&handle, &callbacks);
    if (err == CPG_ERR_TRY_AGAIN) {
        QPID_LOG(notice, "Waiting for CPG initialization.");
        while (CPG_ERR_TRY_AGAIN == (err = cpg_initialize(&handle, &callbacks)))
            sys::sleep(5);
    }
    check(err, "Failed to initialize CPG.");
    check(cpg_context_set(handle, this), "Cannot set CPG context");
    // Note: CPG is currently unix-specific. If CPG is ported to
    // windows then this needs to be refactored into
    // qpid::sys::<platform>
    IOHandle::impl->fd = getFd();
    QPID_LOG(debug, "Initialized CPG handle 0x" << std::hex << handle);
}

Cpg::~Cpg() {
    try {
        shutdown();
    } catch (const std::exception& e) {
        QPID_LOG(error, "Exception in Cpg destructor: " << e.what());
    }
}

void Cpg::join(const std::string& name) {
    group = name;
    check(cpg_join(handle, &group), cantJoinMsg(group));
}
    
void Cpg::leave() {
    check(cpg_leave(handle, &group), cantLeaveMsg(group));
}

bool Cpg::isFlowControlEnabled() {
    cpg_flow_control_state_t flowState;
    check(cpg_flow_control_state_get(handle, &flowState), "Cannot get CPG flow control status.");
    return flowState == CPG_FLOW_CONTROL_ENABLED;
}

bool Cpg::mcast(const iovec* iov, int iovLen) {
    if (isFlowControlEnabled()) {
        QPID_LOG(debug, "CPG flow control enabled")
        return false;
    }
    cpg_error_t result;
    do {
        result = cpg_mcast_joined(handle, CPG_TYPE_AGREED, const_cast<iovec*>(iov), iovLen);
        if (result != CPG_ERR_TRY_AGAIN) check(result, cantMcastMsg(group));
    } while(result == CPG_ERR_TRY_AGAIN);
    return true;
}

void Cpg::shutdown() {
    if (!isShutdown) {
        QPID_LOG(debug,"Shutting down CPG");
        isShutdown=true;
        check(cpg_finalize(handle), "Error in shutdown of CPG");
    }
}

void Cpg::dispatchOne() {
    check(cpg_dispatch(handle,CPG_DISPATCH_ONE), "Error in CPG dispatch");
}

void Cpg::dispatchAll() {
    check(cpg_dispatch(handle,CPG_DISPATCH_ALL), "Error in CPG dispatch");
}

void Cpg::dispatchBlocking() {
    check(cpg_dispatch(handle,CPG_DISPATCH_BLOCKING), "Error in CPG dispatch");
}

string Cpg::errorStr(cpg_error_t err, const std::string& msg) {
    std::ostringstream  os;
    os << msg << ": ";
    switch (err) {
      case CPG_OK: os << "ok"; break;
      case CPG_ERR_LIBRARY: os << "library"; break;
      case CPG_ERR_TIMEOUT: os << "timeout"; break;
      case CPG_ERR_TRY_AGAIN: os << "try again"; break;
      case CPG_ERR_INVALID_PARAM: os << "invalid param"; break;
      case CPG_ERR_NO_MEMORY: os << "no memory"; break;
      case CPG_ERR_BAD_HANDLE: os << "bad handle"; break;
      case CPG_ERR_ACCESS: os << "access denied. You may need to set your group ID to 'ais'"; break;
      case CPG_ERR_NOT_EXIST: os << "not exist"; break;
      case CPG_ERR_EXIST: os << "exist"; break;
      case CPG_ERR_NOT_SUPPORTED: os << "not supported"; break;
      case CPG_ERR_SECURITY: os << "security"; break;
      case CPG_ERR_TOO_MANY_GROUPS: os << "too many groups"; break;
      default: os << ": unknown cpg error " << err;
    };
    os << " (" << err << ")";
    return os.str();
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

MemberId Cpg::self() const {
    unsigned int nodeid;
    check(cpg_local_get(handle, &nodeid), "Cannot get local CPG identity");
    return MemberId(nodeid, getpid());
}

namespace { int byte(uint32_t value, int i) { return (value >> (i*8)) & 0xff; } }

ostream& operator <<(ostream& out, const MemberId& id) {
    out << byte(id.first, 0) << "."
        << byte(id.first, 1) << "."
        << byte(id.first, 2) << "."
        << byte(id.first, 3);
    return out << ":" << id.second;
}

ostream& operator<<(ostream& o, const ConnectionId& c) {
    return o << c.first << "-" << c.second;
}

std::string MemberId::str() const  {
    char s[8];
    reinterpret_cast<uint32_t&>(s[0]) = htonl(first);
    reinterpret_cast<uint32_t&>(s[4]) = htonl(second);
    return std::string(s,8);
}

MemberId::MemberId(const std::string& s) {
    first = ntohl(reinterpret_cast<const uint32_t&>(s[0]));
    second = ntohl(reinterpret_cast<const uint32_t&>(s[4]));
}
}} // namespace qpid::cluster
