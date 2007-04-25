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
#include "qpid/broker/Broker.h"
#include <signal.h>
#include <iostream>
#include <memory>
#include <cerrno>
#include "config.h"
#include <unistd.h>
#include "qpid/sys/posix/check.h"

using namespace qpid;
using namespace qpid::broker;
using namespace qpid::sys;
using namespace std;

/** Command line options */
struct QpiddOptions : public Broker::Options
{
    bool help;
    bool version;
    bool daemon;
    po::options_description desc;
    
    QpiddOptions() :
        help(false), version(false), daemon(false), desc("Options")
    {
        using namespace po;
        desc.add_options()
            ("daemon,d", optValue(daemon), "Run as a daemon");
        Broker::Options::addTo(desc);
        desc.add_options()
            ("help,h", optValue(help), "Print help message")
            ("version,v", optValue(version), "Print version information");
    }

    void parse(int argc, char* argv[]) {
        po::variables_map vm;
        po::store(po::parse_command_line(argc, argv, desc), vm);
        po::notify(vm);
    };
    
    void usage(std::ostream& out) const {
        out << "Usage: qpidd [OPTIONS]" << endl 
            << "Start the Qpid AMQP broker." << endl << endl
            << desc << endl;
    };
};

std::ostream& operator<<(std::ostream& out, const QpiddOptions& config)  {
    config.usage(out); return out;
}

Broker::shared_ptr brokerPtr;

void handle_signal(int /*signal*/){
    if (brokerPtr) {
        cerr << "Shutting down..." << endl;
        brokerPtr->shutdown();
    }
}

int main(int argc, char* argv[])
{
    QpiddOptions config;
    try {
        config.parse(argc, argv);
        if(config.help) {
            config.usage(cout);
        }
        else if (config.version) {
            cout << "qpidd (" << PACKAGE_NAME << ") version "
                 << PACKAGE_VERSION << endl;
        }
        else {
            brokerPtr=Broker::create(config);
            signal(SIGINT, handle_signal);
            if (config.daemon) {
                if (daemon(0, 0) < 0) // daemon(nochdir, noclose)
                    throw QPID_ERROR(
                        INTERNAL_ERROR,
                        "Failed to detach as daemon: "+ strError(errno));
            }
            brokerPtr->run();
        }
        return 0;
    }
    catch(const exception& e) {
        cerr << "Error: " << e.what() << endl
             << "Type 'qpidd --help' for usage." << endl;
    }
    return 1;
}
