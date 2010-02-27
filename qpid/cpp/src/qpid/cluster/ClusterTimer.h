#ifndef QPID_CLUSTER_CLUSTERTIMER_H
#define QPID_CLUSTER_CLUSTERTIMER_H

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

#include "qpid/sys/Timer.h"
#include <map>

namespace qpid {
namespace cluster {

class Cluster;

/**
 * Timer implementation that executes tasks consistently in the
 * deliver thread across a cluster. Task is not executed when timer
 * fires, instead the elder multicasts a wakeup. The task is executed
 * when the wakeup is delivered.
 */
class ClusterTimer : public sys::Timer {
  public:
    ClusterTimer(Cluster&);
    ~ClusterTimer();

    void add(boost::intrusive_ptr<sys::TimerTask> task);

    void deliverWakeup(const std::string& name);
    void deliverDrop(const std::string& name);
    void becomeElder();

  protected:
    void fire(boost::intrusive_ptr<sys::TimerTask> task);
    void drop(boost::intrusive_ptr<sys::TimerTask> task);

  private:
    typedef std::map<std::string, boost::intrusive_ptr<sys::TimerTask> > Map;
    Cluster& cluster;
    Map map;
};


}}


#endif  /*!QPID_CLUSTER_CLUSTERTIMER_H*/
