#ifndef QPID_CLUSTER_TYPES_H
#define QPID_CLUSTER_TYPES_H

/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#include "config.h"
#include "qpid/Url.h"
#include <boost/intrusive_ptr.hpp>
#include <utility>
#include <iosfwd>
#include <string>
#include <stdint.h>

extern "C" {
#if defined (HAVE_OPENAIS_CPG_H)
#  include <openais/cpg.h>
#elif defined (HAVE_COROSYNC_CPG_H)
#  include <corosync/cpg.h>
#else
#  error "No cpg.h header file available"
#endif    
}

namespace qpid {
namespace cluster {

class Connection;
typedef boost::intrusive_ptr<Connection> ConnectionPtr;

/** Types of cluster event. */
enum EventType { DATA, CONTROL };

/** first=node-id, second=pid */
struct MemberId : std::pair<uint32_t, uint32_t> {
    explicit MemberId(uint64_t n) : std::pair<uint32_t,uint32_t>( n >> 32, n & 0xffffffff) {}
    explicit MemberId(uint32_t node=0, uint32_t pid=0) : std::pair<uint32_t,uint32_t>(node, pid) {}
    MemberId(const cpg_address& caddr) : std::pair<uint32_t,uint32_t>(caddr.nodeid, caddr.pid) {}
    MemberId(const std::string&); // Decode from string.
    uint32_t getNode() const { return first; }
    uint32_t getPid() const { return second; }
    operator uint64_t() const { return (uint64_t(first)<<32ull) + second; }

    // AsMethodBody as string, network byte order.
    std::string str() const;
};

inline bool operator==(const cpg_address& caddr, const MemberId& id) { return id == MemberId(caddr); }

std::ostream& operator<<(std::ostream&, const MemberId&);

struct ConnectionId : public std::pair<MemberId, Connection*>  {
    ConnectionId(const MemberId& m=MemberId(), Connection* c=0) :  std::pair<MemberId, Connection*> (m,c) {}
    ConnectionId(uint64_t m, uint64_t c)
        : std::pair<MemberId, Connection*>(MemberId(m), reinterpret_cast<Connection*>(c)) {}
    MemberId getMember() const { return first; }
    Connection* getPointer() const { return second; }
};

std::ostream& operator<<(std::ostream&, const ConnectionId&);

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_TYPES_H*/
