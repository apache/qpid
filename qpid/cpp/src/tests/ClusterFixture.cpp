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
#include <boost/assign.hpp>

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
using boost::assign::list_of;


#include "ClusterFixture.h"

ClusterFixture::ClusterFixture(size_t n, int localIndex_, const Args& args_)
    : name(Uuid(true).str()), localIndex(localIndex_), userArgs(args_)
{
    add(n);
}

const ClusterFixture::Args ClusterFixture::DEFAULT_ARGS =
    list_of<string>("--auth=no")("--no-data-dir");

ClusterFixture::Args ClusterFixture::makeArgs(const std::string& prefix) {
    Args args = list_of<string>("qpidd " __FILE__)
        ("--no-module-dir")
        ("--load-module=../.libs/cluster.so")
        ("--cluster-name")(name) 
        ("--log-prefix")(prefix);
    args.insert(args.end(), userArgs.begin(), userArgs.end());
    return args;
}

void ClusterFixture::add() {
    if (size() != size_t(localIndex))  { // fork a broker process.
        std::ostringstream os; os << "fork" << size();
        std::string prefix = os.str();
        forkedBrokers.push_back(shared_ptr<ForkedBroker>(new ForkedBroker(makeArgs(prefix))));
        push_back(forkedBrokers.back()->getPort());
    }
    else {                      // Run in this process
        addLocal();
    }
}

namespace {
/** Parse broker & cluster options */
Broker::Options parseOpts(size_t argc, const char* argv[]) {
    Broker::Options opts;
    Plugin::addOptions(opts); // Pick up cluster options.
    opts.parse(argc, argv, "", true); // Allow-unknown for --load-module
    return opts;
}
}

void ClusterFixture::addLocal() {
    assert(int(size()) == localIndex);
    ostringstream os; os << "local" << localIndex;
    string prefix = os.str();
    Args args(makeArgs(prefix));
    vector<const char*> argv(args.size());
    transform(args.begin(), args.end(), argv.begin(), boost::bind(&string::c_str, _1));
    qpid::log::Logger::instance().setPrefix(os.str());
    localBroker.reset(new BrokerFixture(parseOpts(argv.size(), &argv[0])));
    push_back(localBroker->getPort());
    forkedBrokers.push_back(shared_ptr<ForkedBroker>());
}

bool ClusterFixture::hasLocal() const { return localIndex >= 0 && size_t(localIndex) < size(); }
    
/** Kill a forked broker with sig, or shutdown localBroker if n==0. */
void ClusterFixture::kill(size_t n, int sig) {
    if (n == size_t(localIndex))
        localBroker->broker->shutdown();
    else
        forkedBrokers[n]->kill(sig);
}

/** Kill a broker and suppressing errors from closing connection c. */
void ClusterFixture::killWithSilencer(size_t n, client::Connection& c, int sig) {
    ScopedSuppressLogging sl;
    kill(n,sig);
    try { c.close(); } catch(...) {}
}
