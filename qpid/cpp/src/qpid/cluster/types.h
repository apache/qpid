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
#include "qpid/RefCounted.h"
#include "qpid/sys/IntegerTypes.h"
#include <boost/intrusive_ptr.hpp>
#include <utility>
#include <iosfwd>
#include <string>

extern "C" {
#if defined (HAVE_OPENAIS_CPG_H)
#  include <openais/cpg.h>

// Provide translations back to the deprecated definitions in openais
typedef cpg_error_t cs_error_t;
#define CS_DISPATCH_ONE          CPG_DISPATCH_ONE
#define CS_DISPATCH_ALL          CPG_DISPATCH_ALL
#define CS_DISPATCH_BLOCKING     CPG_DISPATCH_BLOCKING
#define CS_FLOW_CONTROL_DISABLED CPG_FLOW_CONTROL_DISABLED
#define CS_FLOW_CONTROL_ENABLED  CPG_FLOW_CONTROL_ENABLED
#define CS_OK                    CPG_OK
#define CS_ERR_LIBRARY           CPG_ERR_LIBRARY
#define CS_ERR_TIMEOUT           CPG_ERR_TIMEOUT
#define CS_ERR_TRY_AGAIN         CPG_ERR_TRY_AGAIN
#define CS_ERR_INVALID_PARAM     CPG_ERR_INVALID_PARAM
#define CS_ERR_NO_MEMORY         CPG_ERR_NO_MEMORY
#define CS_ERR_BAD_HANDLE        CPG_ERR_BAD_HANDLE
#define CS_ERR_BUSY              CPG_ERR_BUSY
#define CS_ERR_ACCESS            CPG_ERR_ACCESS
#define CS_ERR_NOT_EXIST         CPG_ERR_NOT_EXIST
#define CS_ERR_EXIST             CPG_ERR_EXIST
#define CS_ERR_NOT_SUPPORTED     CPG_ERR_NOT_SUPPORTED
#define CS_ERR_SECURITY          CPG_ERR_SECURITY
#define CS_ERR_TOO_MANY_GROUPS   CPG_ERR_TOO_MANY_GROUPS

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
    MemberId(uint64_t n=0) : std::pair<uint32_t,uint32_t>( n >> 32, n & 0xffffffff) {}
    MemberId(uint32_t node, uint32_t pid) : std::pair<uint32_t,uint32_t>(node, pid) {}
    MemberId(const cpg_address& caddr) : std::pair<uint32_t,uint32_t>(caddr.nodeid, caddr.pid) {}
    MemberId(const std::string&); // Decode from string.
    uint32_t getNode() const { return first; }
    uint32_t getPid() const { return second; }
    operator uint64_t() const { return (uint64_t(first)<<32ull) + second; }

    // MemberId as byte string, network byte order. Not human readable.
    std::string str() const;
};

inline bool operator==(const cpg_address& caddr, const MemberId& id) { return id == MemberId(caddr); }

std::ostream& operator<<(std::ostream&, const MemberId&);

struct ConnectionId : public std::pair<MemberId, uint64_t>  {
    ConnectionId(const MemberId& m=MemberId(), uint64_t c=0) :  std::pair<MemberId, uint64_t> (m,c) {}
    ConnectionId(uint64_t m, uint64_t c) : std::pair<MemberId, uint64_t>(MemberId(m), c) {}
    MemberId getMember() const { return first; }
    uint64_t getNumber() const { return second; }
};

std::ostream& operator<<(std::ostream&, const ConnectionId&);

std::ostream& operator<<(std::ostream&, EventType);

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_TYPES_H*/
