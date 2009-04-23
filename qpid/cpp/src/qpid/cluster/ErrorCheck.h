#ifndef QPID_CLUSTER_ERRORCHECK_H
#define QPID_CLUSTER_ERRORCHECK_H

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

#include "types.h"
#include "Multicaster.h"
#include "qpid/framing/enum.h"
#include <boost/function.hpp>
#include <deque>
#include <set>

namespace qpid {
namespace cluster {

class EventFrame;
class ClusterMap;
class Cluster;
class Multicaster;
class Connection;

/**
 * Error checking logic.
 * 
 * When an error occurs stop processing frames and queue them until we
 * can determine if all nodes experienced the error. If not, we shut down.
 */
class ErrorCheck
{
  public:
    typedef std::set<MemberId> MemberSet;
    typedef framing::cluster::ErrorType ErrorType;
    
    ErrorCheck(Cluster&);

    /** A local error has occured */
    void error(Connection&, ErrorType, uint64_t frameSeq, const MemberSet&);

    /** Called when a frame is delivered */
    void delivered(const EventFrame&);

    EventFrame getNext();

    bool canProcess() const;
    bool isUnresolved() const;
    
  private:
    void checkResolved();
    
    Cluster& cluster;
    Multicaster& mcast;
    std::deque<EventFrame> frames;
    std::set<MemberId> unresolved;
    uint64_t frameSeq;
    ErrorType type;
    Connection* connection;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_ERRORCHECK_H*/
