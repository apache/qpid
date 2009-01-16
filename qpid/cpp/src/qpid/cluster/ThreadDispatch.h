#ifndef QPID_CLUSTER_THREADDISPATCH_H
#define QPID_CLUSTER_THREADDISPATCH_H

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
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Poller.h"
#include <boost/function.hpp>

namespace qpid {
namespace cluster {

/**
 * Dispatch CPG events in a dedicated thread.
 */
class ThreadDispatch : private sys::Runnable  {
  public:
    ThreadDispatch(Cpg&, boost::shared_ptr<sys::Poller>, boost::function<void()> onError) ;
    ~ThreadDispatch();
    
    void start();

  private:

    Cpg& cpg;
    sys::Thread thread;
    boost::function<void()> onError;
    void run();
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_THREADDISPATCH_H*/
