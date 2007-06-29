#ifndef CLUSTER_H
#define CLUSTER_H

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

#include "qpid/cluster/Cluster.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ChannelOkBody.h"
#include "qpid/framing/BasicGetOkBody.h"
#include "qpid/log/Logger.h"
#include <boost/bind.hpp>
#include <iostream>
#include <vector>

/**
 * Definitions for the Cluster.cpp and Cluster_child.cpp child program.
 */

// using namespace in header file is bad manners, but this is strictly for
// the tests.
using namespace std;
using namespace qpid;
using namespace qpid::cluster;
using namespace qpid::framing;
using namespace qpid::sys;

void null_deleter(void*) {}

struct TestClusterHandler :
    public std::vector<AMQFrame>, public FrameHandler, public Monitor
    
{
    TestClusterHandler(Cluster& c) : cluster(c) {
        cluster.join(make_shared_ptr(this, &null_deleter));
        cluster.setCallback(boost::bind(&Monitor::notify, this));
    }
    
    void handle(AMQFrame& f) {
        ScopedLock l(*this);
        push_back(f);
        notifyAll();
    }

    /** Wait for the vector to contain n frames. */
    bool waitFrames(size_t n) {
        ScopedLock l(*this);
        AbsTime deadline(now(), TIME_SEC);
        while (size() != n && wait(deadline))
            ;
        return size() == n;
    }

    /** Wait for the cluster to have n members */
    bool waitMembers(size_t n) {
        ScopedLock l(*this);
        AbsTime deadline(now(), TIME_SEC);
        while (cluster.size() != n && wait(deadline))
            ;
        return cluster.size() == n;
    }

    Cluster& cluster;
};




#endif  /*!CLUSTER_H*/
