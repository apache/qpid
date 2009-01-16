#ifndef QPID_CLUSTER_POLLERDISPATCH_H
#define QPID_CLUSTER_POLLERDISPATCH_H

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

#include "Cpg.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/DispatchHandle.h"
#include <boost/function.hpp>

namespace qpid {
namespace cluster {

/**
 * Dispatch CPG events via the poller.
 */
class PollerDispatch  {
  public:
    PollerDispatch(Cpg&, boost::shared_ptr<sys::Poller> poller,
                   boost::function<void()> onError) ;
    void start();

  private:
    // Poller callbacks
    void dispatch(sys::DispatchHandle&); // Dispatch CPG events.
    void disconnect(sys::DispatchHandle&); // CPG was disconnected

    Cpg& cpg;
    boost::shared_ptr<sys::Poller> poller;
    boost::function<void()> onError;
    sys::DispatchHandle dispatchHandle;


};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_POLLERDISPATCH_H*/
