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

struct DaemonOptions : public qpid::Options {
    bool daemon;
    bool quit;
    bool check;
    int wait;

    DaemonOptions() : qpid::Options("Daemon options"), daemon(false), quit(false), check(false), wait(10)
    {
        addOptions()
            ("daemon,d", optValue(daemon), "Run as a daemon. With --port 0 print actual listening port.")
            ("wait,w", optValue(wait, "SECONDS"), "Maximum wait for daemon response.")
            ("check,c", optValue(check), "If a daemon is running print its pid to stdout and return 0.")
            ("quit,q", optValue(quit), "Stop the running daemon politely.");
    }
};


struct QpiddOptions : public qpid::Options {
    DaemonOptions daemon;
    Broker::Options broker;
    qpid::log::Options log;
    CommonOptions common;
    
    QpiddOptions() : qpid::Options("Options") {
        common.config = "/etc/qpidd.conf";
        add(common);
        add(broker);
        add(daemon);
        add(log);
    }

    void usage() const {
        cout << "Usage: qpidd [OPTIONS]" << endl << endl << *this << endl;
    };
};

// Globals
Broker::shared_ptr brokerPtr;
QpiddOptions options;

void handle_signal(int /*signal*/){
    QPID_LOG(notice, "Shutting down...");
    brokerPtr->shutdown();
}

/** Compute a name for the pid file */
std::string pidFileFn() {
    uint16_t port=brokerPtr ? brokerPtr->getPort() : options.broker.port;
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
    if (options.broker.port == 0)
        cout << realPort << endl; 
}

/** Code for forked child */
void child(Daemon& demon) {
    brokerPtr=Broker::create(options.broker);
    uint16_t realPort=brokerPtr->getPort();
    demon.ready(realPort);   // Notify parent.
    brokerPtr->run();
}


int main(int argc, char* argv[])
{
    // Spelled 'demon' to avoid clash with daemon.h function.
    Daemon demon(pidFileFn, options.daemon.wait);

    try {
        options.parse(argc, argv, options.common.config);
        qpid::log::Logger::instance().configure(options.log, argv[0]);

        // Options that just print information.
        if(options.common.help || options.common.version) {
            if (options.common.version) 
                cout << "qpidd (" << PACKAGE_NAME << ") version "
                     << PACKAGE_VERSION << endl;
            else if (options.common.help)
                options.usage();
            return 0;
        }

        // Stop running daemon
        if (options.daemon.quit) {
            demon.quit();
            return 0;
        }

        // Query running daemon
        if (options.daemon.check) {
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
        if (options.daemon.daemon) {    // Daemon broker
            demon.fork(parent, child);
        } 
        else {                  // Non-daemon broker.
            brokerPtr = Broker::create(options.broker);
            if (options.broker.port == 0)
                cout << uint16_t(brokerPtr->getPort()) << endl; 
            brokerPtr->run(); 
        }
        return 0;
    }
    catch(const exception& e) {
        if (demon.isParent())
            cerr << e.what() << endl;
        else
            QPID_LOG(critical, e.what());
    }
    return 1;
}
