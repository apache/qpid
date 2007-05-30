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
#include "qpid/sys/posix/check.h"
#include "qpid/broker/Daemon.h"
#include "qpid/log/Statement.h"
#include "qpid/log/Options.h"
#include "qpid/log/Logger.h"
#include "config.h"
#include <iostream>
#include <fstream>
#include <signal.h>

using namespace qpid;
using namespace qpid::broker;
using namespace qpid::sys;
using namespace std;

Broker::shared_ptr brokerPtr;

void handle_signal(int /*signal*/){
    QPID_LOG(notice, "Shutting down...");
    brokerPtr->shutdown();
}


/** Command line options */
struct QpiddOptions : public Broker::Options, public log::Options
{
    bool help;
    bool longHelp;
    bool version;
    bool daemon;
    bool quit;
    bool kill;
    bool check;
    bool ppid;
    int wait;
    string config;
    po::options_description mainOpts;
    po::options_description allOpts;
    po::options_description logOpts;
    
    QpiddOptions() :
        help(false), version(false), daemon(false),
        quit(false), kill(false), check(false), ppid(false), wait(10),
        config("/etc/qpidd.conf"),
        mainOpts("Options"),
        logOpts("Logging Options")
    {
        using namespace po;
        mainOpts.add_options()
            ("daemon,d", optValue(daemon), "Run as a daemon.")
            ("quit,q", optValue(quit), "Stop the running daemon politely.")
            ("kill,k", optValue(kill), "Kill the running daemon harshly.")
            ("check,c", optValue(check), "If daemon is running return 0.")
            ("wait", optValue(wait, "SECONDS"),
             "Maximum wait for daemon response.")
            ("ppid", optValue(ppid), "Print daemon pid to stdout." );
        po::options_description brokerOpts;
        Broker::Options::addTo(mainOpts);
        mainOpts.add_options()
            ("config", optValue(config, "FILE"), "Configuation file.")
            ("help,h", optValue(help), "Print help message.")
            ("long-help", optValue(longHelp), "Show complete list of options.")
            ("version,v", optValue(version), "Print version information.");

        log::Options::addTo(logOpts);
        allOpts.add(mainOpts).add(logOpts);
    }

    void parse(int argc, char* argv[]) {
        parseOptions(allOpts, argc, argv, config);
    }
    
    void usage(const po::options_description& opts) const {
        cout << "Usage: qpidd [OPTIONS]" << endl << endl
                  << opts << endl;
    };
};

int main(int argc, char* argv[])
{
    QpiddOptions config;
    try {
        config.parse(argc, argv);
        if (config.trace)
            config.selectors.push_back("trace");
        log::Logger::instance().configure(config, argv[0]);
        string name=(boost::format("%s.%d")
                     % Daemon::nameFromArgv0(argv[0])
                     % (config.port)).str();
        // Spelled 'demon' to avoid clash with daemon.h function.
        Daemon demon(name, config.wait);

        // Options that just print information.
        if(config.help || config.longHelp || config.version) {
            if (config.version) 
                cout << "qpidd (" << PACKAGE_NAME << ") version "
                     << PACKAGE_VERSION << endl;
            if (config.longHelp)
                config.usage(config.allOpts);
            else if (config.help)
                config.usage(config.mainOpts);
            return 0;
        }

        // Options that act on an already running daemon.
        if (config.quit || config.kill || config.check) {
            pid_t pid = demon.check();
            if (config.ppid && pid > 0)
                cout << pid << endl;
            if (config.kill)
                demon.kill();
            else if (config.quit)
                demon.quit();
            if (config.check && pid <= 0)
                return 1;
            return 0;
        }

        // Starting the broker:
        signal(SIGINT, handle_signal);
        if (config.daemon) {
            pid_t pid = demon.fork();
            if (pid == 0) {  // Child
                try {
                    brokerPtr=Broker::create(config);
                    demon.ready();   // Notify parent we're ready.
                    brokerPtr->run();
                } catch (const exception& e) {
                    QPID_LOG(critical, "Broker daemon startup failed: " << e.what());
                    demon.failed(); // Notify parent we failed.
                    return 1;
                }
            }
            else if (pid > 0) { // Parent
                if (config.ppid)
                    cout << pid << endl;
                return 0;
            }
            else { // pid < 0 
                throw Exception("fork failed"+strError(errno));
            }
        } // Non-daemon broker.
        else {
            brokerPtr = Broker::create(config);
            brokerPtr->run(); 
        }
        return 0;
    }
    catch(const po::error& e) {
        // Command line parsing error.
        cerr << "Error: " << e.what() << endl
             << "Type 'qpidd --help' for usage." << endl;
    }
    catch(const exception& e) {
        // Could be child or parent so log and print.
        QPID_LOG(error, e.what());
        cerr << "Error: " << e.what() << endl;
    }
    return 1;
}
