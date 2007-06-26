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

struct TestCluster : public Cluster {
    TestCluster(const std::string& name,
                const std::string& url,
                framing::FrameHandler& next,
                framing::ProtocolVersion ver) : Cluster(name,url,next, ver) {}

    /** Wait for the cluster to be of expected size (exactly) */
    bool waitFor(size_t n) {
        Mutex::ScopedLock l(lock);
        AbsTime deadline(now(),2*TIME_SEC);
        while(size() != n && lock.wait(deadline))
            ;
        return size() == n;
    }
};
    
struct VectorFrameHandler :
    public std::vector<AMQFrame>, public FrameHandler, public Monitor
    
{
    void handle(AMQFrame& f) {
        ScopedLock l(*this);
        push_back(f);
        notifyAll();
    }

    /** Wait for vector to reach size n exactly */
    bool waitFor(size_t n) {
        ScopedLock l(*this);
        AbsTime deadline(now(), 1*TIME_SEC);
        while (size() != n && wait(deadline))
            ;
        return size() == n;
    }
};


// namespace 



#endif  /*!CLUSTER_H*/
