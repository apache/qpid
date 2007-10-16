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
#include "qpid/Plugin.h"
#include "qpid/sys/Shlib.h"
#include "config.h"
#include <boost/filesystem/path.hpp>
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
            ("daemon,d", optValue(daemon), "Run as a daemon.")
            ("wait,w", optValue(wait, "SECONDS"), "Sets the maximum wait time to initialize the daemon. If the daemon fails to initialize, prints an error and returns 1")
            ("check,c", optValue(check), "Prints the daemon's process ID to stdout and returns 0 if the daemon is running, otherwise returns 1")
            ("quit,q", optValue(quit), "Tells the daemon to shut down");
    }
};


struct QpiddOptions : public qpid::Options {
    CommonOptions common;
    Broker::Options broker;
    DaemonOptions daemon;
    qpid::log::Options log;
    
    QpiddOptions() : qpid::Options("Options"), common("", "/etc/qpidd.conf") {
        add(common);
        add(broker);
        add(daemon);
        add(log);
        const Plugin::Plugins& plugins=
            Plugin::getPlugins();
        for (Plugin::Plugins::const_iterator i = plugins.begin();
             i != plugins.end();
             ++i)
            add(*(*i)->getOptions());
    }

    void usage() const {
        cout << "Usage: qpidd [OPTIONS]" << endl << endl << *this << endl;
    };
};

// Globals
shared_ptr<Broker> brokerPtr;
auto_ptr<QpiddOptions> options;

void shutdownHandler(int signal){
    QPID_LOG(notice, "Shutting down on signal " << signal);
    brokerPtr->shutdown();
}

struct QpiddDaemon : public Daemon {
    /** Code for parent process */
    void parent() {
        uint16_t port = wait(options->daemon.wait);
        if (options->broker.port == 0)
            cout << port << endl; 
    }

    /** Code for forked child process */
    void child() {
        brokerPtr.reset(new Broker(options->broker));
        uint16_t port=brokerPtr->getPort();
        ready(port);            // Notify parent.
        brokerPtr->run();
    }
};

void tryShlib(const char* libname) {
    try {
        Shlib shlib(libname);
    }
    catch (const exception& e) {
        // TODO aconway 2007-07-09: Should log failures as INFO
        // at least, but we try shlibs before logging is configured.
    }
}
  

int main(int argc, char* argv[])
{
    try {
        // Load optional modules
        tryShlib("libqpidcluster.so.0");

        // Parse options
        options.reset(new QpiddOptions());
        options->parse(argc, argv, options->common.config);
        qpid::log::Logger::instance().configure(options->log, argv[0]);

        // Options that just print information.
        if(options->common.help || options->common.version) {
            if (options->common.version) 
                cout << "qpidd (" << PACKAGE_NAME << ") version "
                     << PACKAGE_VERSION << endl;
            else if (options->common.help)
                options->usage();
            return 0;
        }

        // Options that affect a running daemon.
        if (options->daemon.check || options->daemon.quit) {
            pid_t pid = Daemon::getPid(options->broker.port);
            if (pid < 0) 
                return 1;
            if (options->daemon.check)
                cout << pid << endl;
            if (options->daemon.quit && kill(pid, SIGINT) < 0)
                throw Exception("Failed to stop daemon: " + strError(errno));
            return 0;
        }

        // Starting the broker.

        // Signal handling
        signal(SIGINT,shutdownHandler); 
        signal(SIGTERM,shutdownHandler);
        signal(SIGHUP,SIG_IGN); // TODO aconway 2007-07-18: reload config.

        signal(SIGCHLD,SIG_IGN); 
        signal(SIGTSTP,SIG_IGN); 
        signal(SIGTTOU,SIG_IGN);
        signal(SIGTTIN,SIG_IGN);
            
        if (options->daemon.daemon) {
            // Fork the daemon
            QpiddDaemon d;
            d.fork();
        } 
        else {                  // Non-daemon broker.
            brokerPtr.reset(new Broker(options->broker));
            if (options->broker.port == 0)
                cout << uint16_t(brokerPtr->getPort()) << endl; 
            brokerPtr->run(); 
        }
        return 0;
    }
    catch(const exception& e) {
        cerr << e.what() << endl;
    }
    return 1;
}
