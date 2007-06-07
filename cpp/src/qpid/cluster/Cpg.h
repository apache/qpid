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

#include <stdexcept>
#include <cassert>
#ifdef CLUSTER
extern "C" {
#include <openais/cpg.h>
}
#endif
namespace qpid {
namespace cluster {

/**
 * Lightweight C++ interface to cpg.h operations. 
 * Manages a single CPG handle, initialized in ctor, finialzed in destructor.
 * On error all functions throw Cpg::Exception
 */
class Cpg {
  public:
    // FIXME aconway 2007-06-01: qpid::Exception
    struct Exception : public std::runtime_error {
        Exception(const std::string& msg) : runtime_error(msg) {}
    };

    struct Name : public cpg_name {
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
    
    static inline std::string str(const cpg_name& n) {
        return std::string(n.value, n.length);
    }

    // TODO aconway 2007-06-01: when cpg handle supports a context pointer
    // use callback objects (boost::function) instead of free functions.
    // 
    /** Open a CPG handle.
     *@param deliver - free function called when a message is delivered.
     *@param reconfig - free function called when CPG configuration changes.
     */
    Cpg(cpg_deliver_fn_t deliver, cpg_confchg_fn_t reconfig) {
        cpg_callbacks_t callbacks = { deliver, reconfig };
        check(cpg_initialize(&handle, &callbacks), "Cannot initialize CPG");
    }

    /** Disconnect */
    ~Cpg() {
        check(cpg_finalize(handle), "Cannot finalize CPG");
    }

    /** Dispatch CPG events.
     *@param type one of
     * - CPG_DISPATCH_ONE - dispatch exactly one event.
     * - CPG_DISPATCH_ALL - dispatch all available events, don't wait.
     * - CPG_DISPATCH_BLOCKING - blocking dispatch loop.
     */
    void dispatch(cpg_dispatch_t type) {
        check(cpg_dispatch(handle,type), "Error in CPG dispatch");
    }

    void join(const Name& group) {
        check(cpg_join(handle, const_cast<Name*>(&group)),cantJoinMsg(group));
    };
    
    void leave(const Name& group) {
        check(cpg_leave(handle,const_cast<Name*>(&group)),cantLeaveMsg(group));
    }

    void mcast(const Name& group, const iovec* iov, int iovLen) {
        check(cpg_mcast_joined(
                  handle, CPG_TYPE_AGREED, const_cast<iovec*>(iov), iovLen),
              cantMcastMsg(group));
    }

  private:
    static std::string errorStr(cpg_error_t err, const std::string& msg);
    static std::string cantJoinMsg(const Name&);
    static std::string cantLeaveMsg(const Name&);
    static std::string cantMcastMsg(const Name&);
    
    static void check(cpg_error_t result, const std::string& msg) {
        // TODO aconway 2007-06-01: Logging and exceptions.
        if (result != CPG_OK) 
            throw Exception(errorStr(result, msg));
    }
    cpg_handle_t handle;
};



}} // namespace qpid::cluster



#endif  /*!CPG_H*/
