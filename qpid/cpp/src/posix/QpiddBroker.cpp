/*
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

#include "config.h"
#include "qpidd.h"
#include "qpid/Exception.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Daemon.h"
#include "qpid/broker/SignalHandler.h"
#include "qpid/log/Logger.h"

#include <iostream>
#include <fstream>
#include <signal.h>
#include <unistd.h>
#include <sys/utsname.h>

using std::cout;
using std::endl;
using std::ifstream;
using std::ofstream;

namespace qpid {
namespace broker {

BootstrapOptions::BootstrapOptions(const char* argv0)
    : qpid::Options("Options"),
      common("", QPIDD_CONF_FILE, QPIDC_CONF_FILE),
      module(QPIDD_MODULE_DIR),
      log(argv0)
{
    add(common);
    add(module);
    add(log);
}

void BootstrapOptions::usage() const {
    cout << "Usage: qpidd [OPTIONS]" << endl << endl << *this << endl;
}

namespace {
const std::string TCP = "tcp";
}

struct DaemonOptions : public qpid::Options {
    bool daemon;
    bool quit;
    bool kill;
    bool check;
    std::vector<int> closeFd;
    int wait;
    std::string piddir;
    std::string pidfile;
    std::string transport;

    DaemonOptions() : qpid::Options("Daemon options"), daemon(false), quit(false), kill(false), check(false), wait(600), transport(TCP)
    {
        char *home = ::getenv("HOME");

        if (home == 0)
            piddir += "/tmp";
        else
            piddir += home;
        piddir += "/.qpidd";

        addOptions()
            ("daemon,d", pure_switch(daemon), "Run as a daemon. Logs to syslog by default in this mode.")
            ("transport", optValue(transport, "TRANSPORT"), "The transport for which to return the port")
            ("pid-dir", optValue(piddir, "DIR"), "Directory where port-specific PID file is stored")
            ("pidfile", optValue(pidfile, "FILE"), "File name to store the PID in daemon mode. Used as-is, no directory or suffixes added.")
            ("close-fd", optValue(closeFd, "FD"), "File descriptors that the daemon should close")
            ("wait,w", optValue(wait, "SECONDS"), "Sets the maximum wait time to initialize or shutdown the daemon. If the daemon fails to initialize/shutdown, prints an error and returns 1")
            ("check,c", pure_switch(check), "Prints the daemon's process ID to stdout and returns 0 if the daemon is running, otherwise returns 1")
            ("quit,q", pure_switch(quit), "Tells the daemon to shut down with an INT signal")
            ("kill,k", pure_switch(kill), "Kill the daemon with a KILL signal.");
    }
};

struct QpiddPosixOptions : public QpiddOptionsPrivate {
    DaemonOptions daemon;
    QpiddOptions *parent;

    QpiddPosixOptions(QpiddOptions *parent_) : parent(parent_) {
        parent->add(daemon);
    }
};

QpiddOptions::QpiddOptions(const char* argv0)
    : qpid::Options("Options"),
      common("", QPIDD_CONF_FILE, QPIDC_CONF_FILE),
      module(QPIDD_MODULE_DIR),
      log(argv0)
{
    add(common);
    add(module);
    add(broker);
    add(log);

    platform.reset(new QpiddPosixOptions(this));
    qpid::Plugin::addOptions(*this);
}

void QpiddOptions::usage() const {
    cout << "Usage: qpidd [OPTIONS]" << endl << endl << *this << endl;
}

// Set the broker pointer on the signal handler, then reset at end of scope.
// This is to ensure that the signal handler doesn't keep a broker
// reference after main() has returned.
//
struct ScopedSetBroker {
    ScopedSetBroker(const boost::intrusive_ptr<Broker>& broker) {
        qpid::broker::SignalHandler::setBroker(broker.get());
    }
    ~ScopedSetBroker() { qpid::broker::SignalHandler::setBroker(0); }
};

namespace {

/// Write a pid file if requested
void writePid(const std::string& filename) {
    if (!filename.empty()) {
        ofstream pidfile(filename.c_str());
        pidfile << ::getpid() << endl;
        pidfile.close();
    }
}
}

struct QpiddDaemon : public Daemon {
    QpiddPosixOptions *options;

    QpiddDaemon(std::string pidDir, QpiddPosixOptions *opts)
        : Daemon(pidDir), options(opts) {}

    /** Code for parent process */
    void parent() {
        uint16_t port = wait(options->daemon.wait);
        if (options->parent->broker.port == 0)
            cout << port << endl;
    }

    /** Code for forked child process */
    void child() {
        // Close extra FDs requested in options.
        for (size_t i = 0; i < options->daemon.closeFd.size(); ++i)
            ::close(options->daemon.closeFd[i]);
        boost::intrusive_ptr<Broker> brokerPtr(new Broker(options->parent->broker));
        ScopedSetBroker ssb(brokerPtr);
        brokerPtr->accept();
        uint16_t port=brokerPtr->getPort(options->daemon.transport);
        ready(port);            // Notify parent.
        if (options->parent->broker.enableMgmt && (options->parent->broker.port == 0 || options->daemon.transport != TCP)) {
            boost::dynamic_pointer_cast<qmf::org::apache::qpid::broker::Broker>(brokerPtr->GetManagementObject())->set_port(port);
        }
        writePid(options->daemon.pidfile);
        brokerPtr->run();
    }
};

int QpiddBroker::execute (QpiddOptions *options) {
    // Options that affect a running daemon.
    QpiddPosixOptions *myOptions =
        static_cast<QpiddPosixOptions *>(options->platform.get());
    if (myOptions == 0)
        throw Exception("Internal error obtaining platform options");

    if (myOptions->daemon.check || myOptions->daemon.quit || myOptions->daemon.kill) {
        pid_t pid = 0;
        if (!myOptions->daemon.pidfile.empty()) {
            ifstream pidfile(myOptions->daemon.pidfile.c_str());
            pidfile >> pid;
            pidfile.close();
        }
        if (pid == 0) {
            try {
                pid = Daemon::getPid(myOptions->daemon.piddir, options->broker.port);
            } catch (const Exception& e) {
                // This is not a critical error, usually means broker is not running
                QPID_LOG(notice, "Broker is not running: " << e.what());
                return 1;
            }
        }
        if (pid < 0)
            return 1;
        if (myOptions->daemon.check)
            cout << pid << endl;
        if (myOptions->daemon.quit || myOptions->daemon.kill) {
            int signal = myOptions->daemon.kill ? SIGKILL : SIGINT;
            if (kill(pid, signal) < 0)
                throw Exception("Failed to stop daemon: " + qpid::sys::strError(errno));
            // Wait for the process to die before returning
            int retry=myOptions->daemon.wait*1000;    // Try up to "--wait N" seconds, do retry every millisecond
            while (kill(pid,0) == 0 && --retry)
                sys::usleep(1000);
            if (retry == 0)
                throw Exception("Gave up waiting for daemon process to exit");
        }
        return 0;
    }

    // Starting the broker.
    if (myOptions->daemon.daemon) {
        // For daemon mode replace default stderr with syslog.
        options->log.sinkOptions->detached();
        qpid::log::Logger::instance().configure(options->log);
        // Fork the daemon
        QpiddDaemon d(myOptions->daemon.piddir, myOptions);
        d.fork();           // Broker is stared in QpiddDaemon::child()
    }
    else {                  // Non-daemon broker.
        boost::intrusive_ptr<Broker> brokerPtr(new Broker(options->broker));
        ScopedSetBroker ssb(brokerPtr);
        brokerPtr->accept();
        if (options->broker.port == 0) {
            uint16_t port = brokerPtr->getPort(myOptions->daemon.transport);
            cout << port << endl;
            if (options->broker.enableMgmt) {
                boost::dynamic_pointer_cast<qmf::org::apache::qpid::broker::Broker>(brokerPtr->GetManagementObject())->set_port(port);
            }
        }
        writePid(myOptions->daemon.pidfile);
        brokerPtr->run();
    }
    return 0;
}

}} // namespace qpid::Broker

int main(int argc, char* argv[])
{
    return qpid::broker::run_broker(argc, argv);
}
