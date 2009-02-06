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



#include "ClusterFixture.h"


/** Parse broker & cluster options */
Broker::Options parseOpts(size_t argc, const char* argv[]) {
    Broker::Options opts;
    Plugin::addOptions(opts); // Pick up cluster options.
    opts.parse(argc, argv, "", true); // Allow-unknown for --load-module
    return opts;
}




ClusterFixture::ClusterFixture(size_t n, int localIndex_) : name(Uuid(true).str()), localIndex(localIndex_) {
    add(n);
}

void ClusterFixture::add() {
    if (size() != size_t(localIndex))  { // fork a broker process.
        std::ostringstream os; os << "fork" << size();
        std::string prefix = os.str();
        const char* argv[] = {
            "qpidd " __FILE__ ,
            "--no-module-dir",
            "--load-module=../.libs/cluster.so",
            "--cluster-name", name.c_str(), 
            "--auth=no", "--no-data-dir",
            "--log-prefix", prefix.c_str(),
        };
        size_t argc = sizeof(argv)/sizeof(argv[0]);
        forkedBrokers.push_back(shared_ptr<ForkedBroker>(new ForkedBroker(argc, argv)));
        push_back(forkedBrokers.back()->getPort());
    }
    else {                      // Run in this process
        addLocal();
    }
}

void ClusterFixture::addLocal() {
    assert(int(size()) == localIndex || localIndex == -1);
    localIndex = size();
    const char* argv[] = {
        "qpidd " __FILE__ ,
        "--load-module=../.libs/cluster.so",
        "--cluster-name", name.c_str(), 
        "--auth=no", "--no-data-dir"
    };
    size_t argc = sizeof(argv)/sizeof(argv[0]);
    ostringstream os; os << "local" << localIndex;
    qpid::log::Logger::instance().setPrefix(os.str());
    localBroker.reset(new BrokerFixture(parseOpts(argc, argv)));
    push_back(localBroker->getPort());
    forkedBrokers.push_back(shared_ptr<ForkedBroker>());
}



