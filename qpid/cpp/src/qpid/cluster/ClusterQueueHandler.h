#ifndef QPID_CLUSTER_CLUSTERQUEUEHANDLER_H
#define QPID_CLUSTER_CLUSTERQUEUEHANDLER_H

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

#include "Cluster.h"
#include "qpid/sys/PollableQueue.h"
#include  <qpid/log/Statement.h>

namespace qpid {
namespace cluster {

/** Convenience functor for PollableQueue callbacks. */
template <class T> struct ClusterQueueHandler {
    ClusterQueueHandler(Cluster& c, boost::function<void (const T&)> f, const std::string& n) : cluster(c), callback(f), name(n) {}
    ClusterQueueHandler(const Cluster* c, boost::function<void (const T&)> f, const std::string& n) : cluster(*const_cast<Cluster*>(c)), callback(f), name(n) {}

    void operator()(typename sys::PollableQueue<T>::Queue& values) {
        try {
            std::for_each(values.begin(), values.end(), callback);
            values.clear();
        }
        catch (const std::exception& e) {
            QPID_LOG(error, "Error on " << name << ": " << e.what());
            cluster.leave();
        }
    }

    Cluster& cluster;
    boost::function<void (const T&)> callback;
    std::string name;
};

    
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CLUSTERQUEUEHANDLER_H*/
