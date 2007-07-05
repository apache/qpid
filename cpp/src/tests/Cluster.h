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
#include <functional>

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
using namespace boost;

void null_deleter(void*) {}

template <class T>
struct TestHandler : public Handler<T&>, public vector<T>, public Monitor
{
    void handle(T& frame) {
        Mutex::ScopedLock l(*this);
        push_back(frame);
        notifyAll();
    }

    bool waitFor(size_t n) {
        Mutex::ScopedLock l(*this);
        AbsTime deadline(now(), 5*TIME_SEC);
        while (vector<T>::size() != n && wait(deadline))
            ;
        return vector<T>::size() == n;
    }
};

typedef TestHandler<AMQFrame> TestFrameHandler;
typedef TestHandler<SessionFrame> TestSessionFrameHandler;

void nullDeleter(void*) {}

struct TestCluster : public Cluster
{
    TestCluster(string name, string url) : Cluster(name, url)
    {
        setReceivedChain(make_shared_ptr(&received, nullDeleter));
    }

    /** Wait for cluster to be of size n. */
    bool waitFor(size_t n) {
        return wait(boost::bind(equal_to<size_t>(), bind(&Cluster::size,this), n));
    }

    TestSessionFrameHandler received;
};



#endif  /*!CLUSTER_H*/
