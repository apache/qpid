#ifndef CLUSTER_FIXTURE_H
#define CLUSTER_FIXTURE_H

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

#include "test_tools.h"
#include "unit_test.h"
#include "ForkedBroker.h"
#include "BrokerFixture.h"

#include "qpid/client/Connection.h"
#include "qpid/client/ConnectionAccess.h"
#include "qpid/client/Session.h"
#include "qpid/client/FailoverListener.h"
#include "qpid/cluster/Cluster.h"
#include "qpid/cluster/Cpg.h"
#include "qpid/cluster/UpdateClient.h"
#include "qpid/framing/AMQBody.h"
#include "qpid/framing/Uuid.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/enum.h"
#include "qpid/log/Logger.h"

#include <boost/bind.hpp>
#include <boost/shared_ptr.hpp>

#include <string>
#include <iostream>
#include <iterator>
#include <vector>
#include <set>
#include <algorithm>
#include <iterator>


using namespace std;
using namespace qpid;
using namespace qpid::cluster;
using namespace qpid::framing;
using namespace qpid::client;
using qpid::sys::TIME_SEC;
using qpid::broker::Broker;
using boost::shared_ptr;
using qpid::cluster::Cluster;



/** Cluster fixture is a vector of ports for the replicas.
 * 
 * At most one replica (by default replica 0) is in the current
 * process, all others are forked as children.
 */
class ClusterFixture : public vector<uint16_t>  {
    string name;
    std::auto_ptr<BrokerFixture> localBroker;
    int localIndex;
    std::vector<shared_ptr<ForkedBroker> > forkedBrokers;

  public:
    /** @param localIndex can be -1 meaning don't automatically start a local broker.
     * A local broker can be started with addLocal().
     */
    ClusterFixture(size_t n, int localIndex=0);
    void add(size_t n) { for (size_t i=0; i < n; ++i) add(); }
    void add();                 // Add a broker.
    void addLocal();            // Add a local broker.
    void setup();

    bool hasLocal() const { return localIndex >= 0 && size_t(localIndex) < size(); }
    
    /** Kill a forked broker with sig, or shutdown localBroker if n==0. */
    void kill(size_t n, int sig=SIGINT) {
        if (n == size_t(localIndex))
            localBroker->broker->shutdown();
        else
            forkedBrokers[n]->kill(sig);
    }

    /** Kill a broker and suppressing errors from closing connection c. */
    void killWithSilencer(size_t n, client::Connection& c, int sig=SIGINT) {
        ScopedSuppressLogging sl;
        kill(n,sig);
        try { c.close(); } catch(...) {}
    }
};


#endif  /*!CLUSTER_FIXTURE_H*/
