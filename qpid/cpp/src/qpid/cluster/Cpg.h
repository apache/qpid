#ifndef CPG_H
#define CPG_H

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

#include "qpid/Exception.h"
#include "qpid/cluster/Dispatchable.h"
#include "qpid/cluster/types.h"
#include "qpid/sys/IOHandle.h"
#include "qpid/sys/Mutex.h"

#include <boost/scoped_ptr.hpp>

#include <cassert>
#include <string.h>

namespace qpid {
namespace cluster {

/**
 * Lightweight C++ interface to cpg.h operations.
 * 
 * Manages a single CPG handle, initialized in ctor, finialzed in destructor.
 * On error all functions throw Cpg::Exception.
 *
 */
class Cpg : public sys::IOHandle {
  public:
    struct Exception : public ::qpid::Exception {
        Exception(const std::string& msg) : ::qpid::Exception(msg) {}
    };

    struct Name : public cpg_name {
        Name() { length = 0; }
        Name(const char* s) { copy(s, strlen(s)); }
        Name(const char* s, size_t n) { copy(s,n); }
        Name(const std::string& s) { copy(s.data(), s.size()); }
        void copy(const char* s, size_t n) {
            assert(n < CPG_MAX_NAME_LENGTH);
            memcpy(value, s, n);
            length=n;
        }

        std::string str() const { return std::string(value, length); }
    };

    static std::string str(const cpg_name& n) {
        return std::string(n.value, n.length);
    }

    struct Handler {
        virtual ~Handler() {};
        virtual void deliver(
            cpg_handle_t /*handle*/,
            struct cpg_name *group,
            uint32_t /*nodeid*/,
            uint32_t /*pid*/,
            void* /*msg*/,
            int /*msg_len*/) = 0;

        virtual void configChange(
            cpg_handle_t /*handle*/,
            struct cpg_name */*group*/,
            struct cpg_address */*members*/, int /*nMembers*/,
            struct cpg_address */*left*/, int /*nLeft*/,
            struct cpg_address */*joined*/, int /*nJoined*/
        ) = 0;
    };

    /** Open a CPG handle.
     *@param handler for CPG events.
     */
    Cpg(Handler&);
    
    /** Destructor calls shutdown if not already calledx. */
    ~Cpg();

    /** Disconnect from CPG */
    void shutdown();
    
    void dispatchOne();
    void dispatchAll();
    void dispatchBlocking();

    void join(const std::string& group);    
    void leave();

    /** Multicast to the group. NB: must not be called concurrently.
     * 
     *@return true if the message was multi-cast, false if
     * it was not sent due to flow control.
     */
    bool mcast(const iovec* iov, int iovLen);

    cpg_handle_t getHandle() const { return handle; }

    MemberId self() const;

    int getFd();
    
    bool isFlowControlEnabled();
    
  private:
    static std::string errorStr(cpg_error_t err, const std::string& msg);
    static std::string cantJoinMsg(const Name&);
    static std::string cantLeaveMsg(const Name&);
    static std::string cantMcastMsg(const Name&);

    static void check(cpg_error_t result, const std::string& msg) {
        if (result != CPG_OK) throw Exception(errorStr(result, msg));
    }

    static Cpg* cpgFromHandle(cpg_handle_t);

    static void globalDeliver(
        cpg_handle_t handle,
        struct cpg_name *group,
        uint32_t nodeid,
        uint32_t pid,
        void* msg,
        int msg_len);

    static void globalConfigChange(
        cpg_handle_t handle,
        struct cpg_name *group,
        struct cpg_address *members, int nMembers,
        struct cpg_address *left, int nLeft,
        struct cpg_address *joined, int nJoined
    );

    cpg_handle_t handle;
    Handler& handler;
    bool isShutdown;
    Name group;
    sys::Mutex dispatchLock;
};

inline bool operator==(const cpg_name& a, const cpg_name& b) {
    return a.length==b.length &&  strncmp(a.value, b.value, a.length) == 0;
}
inline bool operator!=(const cpg_name& a, const cpg_name& b) { return !(a == b); }

}} // namespace qpid::cluster

#endif  /*!CPG_H*/
