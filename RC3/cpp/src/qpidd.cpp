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
#include <Broker.h>
#include <iostream>
#include <memory>
#include <config.h>
#include <fstream>
#include <signal.h>
#include <Daemon.h>
#include <boost/format.hpp>

using boost::format;
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
    bool quit;
    bool kill;
    bool check;
    int wait;
    string config;
    po::options_description desc;

    QpiddOptions() :
        help(false), version(false), daemon(false),
        quit(false), kill(false), check(false), wait(10),
        config("/etc/qpidd.conf"),
        desc("Options")
    {
        using namespace po;
        desc.add_options()
            ("daemon,d", optValue(daemon), "Run as a daemon.")
            ("quit,q", optValue(quit), "Stop the running daemon politely.")
            ("kill,k", optValue(kill), "Kill the running daemon harshly.")
            ("check,c", optValue(check), "If daemon is running print PID and return 0.")
            ("wait", optValue(wait, "SECONDS"), "Maximum wait for daemon response.");
        po::options_description brokerOpts;
        Broker::Options::addTo(desc);
        desc.add_options()
            ("config", optValue(config, "FILE"), "Configuation file")
            ("help,h", optValue(help), "Print help message")
            ("version,v", optValue(version), "Print version information");
    }

    void parse(int argc, char* argv[]) {
        po::variables_map vm;
        // Earlier sources get precedence.
        po::store(po::parse_command_line(argc, argv, desc), vm);
        try { 
            po::store(po::parse_environment(desc, po::EnvMapper(desc)), vm);
        }
        catch (const logic_error& e) {
            throw logic_error(string("parsing environment variables: ")
                              + e.what());
        }
        po::notify(vm);         // So we can use the value of config.
        try {
            ifstream conf(config.c_str());
            po::store(po::parse_config_file(conf, desc), vm);
    }
        catch (const logic_error& e) {
            throw logic_error(string("parsing config file: ")+ e.what());
        }
        po::notify(vm);
};

    void usage(ostream& out) const {
        out << "Usage: qpidd [OPTIONS]" << endl << endl
            << desc << endl;
    };
};

ostream& operator<<(ostream& out, const QpiddOptions& config)  {
    config.usage(out); return out;
}

// Globals
Broker::shared_ptr brokerPtr;
QpiddOptions options;

void shutdownHandler(int /*signal*/){
    brokerPtr->shutdown();
}

struct QpiddDaemon : public Daemon {
    /** Code for parent process */
    void parent() {
        uint16_t port = wait(options.wait);
        if (options.port == 0)
            cout << port << endl; 
    }

    /** Code for forked child process */
    void child() {
        brokerPtr = Broker::create(options);
        uint16_t port=brokerPtr->getPort();
        ready(port);            // Notify parent.
        brokerPtr->run();
    }
};


int main(int argc, char* argv[])
{
    try {
        options.parse(argc, argv);

        // Options that just print information.
        if(options.help || options.version) {
            if (options.version) 
                cout << "qpidd (" << PACKAGE_NAME << ") version "
                     << PACKAGE_VERSION << endl;
            else if (options.help)
                options.usage(cout);
            return 0;
        }

        // Options that affect a running daemon.
        if (options.check || options.quit) {
            pid_t pid = Daemon::getPid(options.port);
            if (pid < 0) 
                return 1;
            if (options.check)
                cout << pid << endl;
            if (options.quit && kill(pid, SIGINT) < 0)
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
            
        if (options.daemon) {
            // Fork the daemon
            QpiddDaemon d;
            d.fork();
        } 
        else {                  // Non-daemon broker.
            brokerPtr =  Broker::create(options);
            if (options.port == 0)
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
