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

// Global vector of Cpg pointers by handle.
// TODO aconway 2007-06-12: Replace this with cpg_get/set_context,
// coming in in RHEL 5.1.
class Cpg::Handles
{
  public:
    void put(cpg_handle_t handle, Cpg::Handler* handler) {
        sys::Mutex::ScopedLock l(lock);
        uint32_t index=uint32_t(handle); // Lower 32 bits is an array index.
        if (index >= handles.size())
            handles.resize(index+1, 0);
        handles[index] = handler;
    }
    
    Cpg::Handler* get(cpg_handle_t handle) {
        sys::Mutex::ScopedLock l(lock);
        uint32_t index=uint32_t(handle); // Lower 32 bits is an array index.
        assert(index < handles.size());
        assert(handles[index]);
        return handles[index];
    }
    
  private:
    sys::Mutex lock;
    vector<Cpg::Handler*>  handles;
};

Cpg::Handles Cpg::handles;

// Global callback functions call per-object callbacks via handles vector.
void Cpg::globalDeliver (
    cpg_handle_t handle,
    struct cpg_name *group,
    uint32_t nodeid,
    uint32_t pid,
    void* msg,
    int msg_len)
{
    Cpg::Handler* handler=handles.get(handle);
    if (handler)
        handler->deliver(handle, group, nodeid, pid, msg, msg_len);
}

void Cpg::globalConfigChange(
    cpg_handle_t handle,
    struct cpg_name *group,
    struct cpg_address *members, int nMembers,
    struct cpg_address *left, int nLeft,
    struct cpg_address *joined, int nJoined
)
{
    Cpg::Handler* handler=handles.get(handle);
    if (handler)
        handler->configChange(handle, group, members, nMembers, left, nLeft, joined, nJoined);
}

Cpg::Cpg(Handler& h) : handler(h) {
    cpg_callbacks_t callbacks = { &globalDeliver, &globalConfigChange };
    check(cpg_initialize(&handle, &callbacks), "Cannot initialize CPG");
    handles.put(handle, &handler);
    QPID_LOG(debug, "Initialize CPG handle 0x" << std::hex << handle);
}

Cpg::~Cpg() {
    try {
        shutdown();
    } catch (const std::exception& e) {
        QPID_LOG(error, "Exception in Cpg destructor: " << e.what());
    }
}

void Cpg::shutdown() {
    if (handles.get(handle)) {
        QPID_LOG(debug, "Finalize CPG handle " << std::hex << handle);
        handles.put(handle, 0);
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



