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
#include <boost/filesystem/path.hpp>
#include <boost/filesystem/operations.hpp>
#include <iostream>
#include <fstream>
#include <signal.h>
#include <unistd.h>

using namespace qpid;
using namespace qpid::broker;
using namespace qpid::sys;
using namespace std;

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
    int wait;
    string config;
    po::options_description mainOpts;
    po::options_description allOpts;
    
    QpiddOptions() :
        help(false), version(false), daemon(false),
        quit(false), check(false), 
        wait(10),
        config("/etc/qpidd.conf"),
        mainOpts("Broker Options")
    {
        using namespace po;
        // First set up the sub-option groups.
        options_description daemonOpts("Daemon Options");
        daemonOpts.add_options()
            ("daemon,d", optValue(daemon), "Run as a daemon. With --port 0 print actual listening port.")
            ("wait,w", optValue(wait, "SECONDS"), "Maximum wait for daemon response.")
            ("check,c", optValue(check), "If a daemon is running print its pid to stdout and return 0.")
            ("quit,q", optValue(quit), "Stop the running daemon politely.");

        options_description logOpts("Logging Options");
        log::Options::addTo(logOpts);

        // Populate the main options group for --help
        Broker::Options::addTo(mainOpts);
        mainOpts.add_options()
            ("config", optValue(config, "FILE"), "Configuation file.")
            ("help,h", optValue(help), "Print help message.")
            ("long-help", optValue(longHelp), "Show complete list of options.")
            ("version,v", optValue(version), "Print version information.");
        mainOpts.add(daemonOpts);

        // Populate the all options group
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

// Globals
Broker::shared_ptr brokerPtr;
QpiddOptions config;

void handle_signal(int /*signal*/){
    QPID_LOG(notice, "Shutting down...");
    brokerPtr->shutdown();
}

/** Compute a name for the pid file */
std::string pidFileFn() {
    uint16_t port=brokerPtr ? brokerPtr->getPort() : config.port;
    string file=(boost::format("qpidd.%d.pid") % port).str();
    string pidPath;
    if (getuid() == 0)          // Use standard pid file for root.
        pidPath=Daemon::defaultPidFile(file);
    else {                      // Use $HOME/.qpidd for non-root.
        const char* home=getenv("HOME");
        if (!home)
            throw(Exception("$HOME is not set, cant create $HOME/.qpidd."));
        namespace fs=boost::filesystem;
        fs::path dir = fs::path(home,fs::native) / fs::path(".qpidd", fs::native);
        fs::create_directory(dir);
        dir /= file;
        pidPath=dir.string();
    }
    QPID_LOG(debug, "PID file name=" << pidPath);
    return pidPath;
}

/** Code for forked parent */
void parent(Daemon& demon) {
    uint16_t realPort = demon.wait();
    if (config.port == 0)
        cout << realPort << endl; 
}

/** Code for forked child */
void child(Daemon& demon) {
    brokerPtr=Broker::create(config);
    uint16_t realPort=brokerPtr->getPort();
    demon.ready(realPort);   // Notify parent.
    brokerPtr->run();
}


int main(int argc, char* argv[])
{
    // Spelled 'demon' to avoid clash with daemon.h function.
    Daemon demon(pidFileFn, config.wait);

    try {
        config.parse(argc, argv);
        if (config.trace)
            config.selectors.push_back("trace+");
        log::Logger::instance().configure(config, argv[0]);

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

        // Stop running daemon
        if (config.quit) {
            demon.quit();
            return 0;
        }

        // Query running daemon
        if (config.check) {
            pid_t pid = demon.check();
            if (pid < 0) 
                return 1;
            else {
                cout << pid << endl;
                return 0;
            }
        }

        // Starting the broker:
        signal(SIGINT, handle_signal);
        if (config.daemon) {    // Daemon broker
            demon.fork(parent, child);
        } 
        else {                  // Non-daemon broker.
            brokerPtr = Broker::create(config);
            if (config.port == 0)
                cout << uint16_t(brokerPtr->getPort()) << endl; 
            brokerPtr->run(); 
        }
        return 0;
    }
    catch(const po::error& e) {
        // Command line parsing error.
        cerr << "Error: " << e.what() << endl
             << "Type 'qpidd --long-help' for full usage." << endl;
    }
    catch(const exception& e) {
        if (demon.isParent())
            cerr << "Error: " << e.what() << endl;
        else
            QPID_LOG(critical, e.what());
    }
    return 1;
}
