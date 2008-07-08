#ifndef TESTS_FORKEDBROKER_H
#define TESTS_FORKEDBROKER_H

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

#include "qpid/Exception.h"
#include "qpid/sys/Fork.h"
#include "qpid/log/Logger.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/SignalHandler.h"

#include <boost/lexical_cast.hpp>

#include <string>

#include <signal.h>
#include <sys/wait.h>

/**
 * Class to fork a broker child process.
 * 
 * For most tests a BrokerFixture may be more convenient as it starts
 * a broker in the same process which allows you to easily debug into
 * the broker.
 *
 * This useful for tests that need to start multiple brokers where
 * those brokers can't coexist in the same process (e.g. for cluster
 * tests where CPG doesn't allow multiple group members in a single
 * process.)
 * 
 */
class ForkedBroker : public qpid::sys::ForkWithMessage {
    pid_t pid;
    uint16_t port;
    qpid::broker::Broker::Options opts;
    std::string prefix;

  public:
    struct ChildExit {};   // Thrown in child processes.

    ForkedBroker(const qpid::broker::Broker::Options& opts_=qpid::broker::Broker::Options(),
                 const std::string& prefix_=std::string())
        : pid(0), port(0), opts(opts_), prefix(prefix_) { fork(); } 

    ~ForkedBroker() {
        try { stop(); }
        catch(const std::exception& e) {
            QPID_LOG(error, e.what());
        }
    }

    void stop() {
        if (pid > 0) {     // I am the parent, clean up children.
            if (::kill(pid, SIGINT) < 0)
                throw qpid::Exception(QPID_MSG("Can't kill process " << pid << ": " << qpid::strError(errno)));
            int status = 0;
            if (::waitpid(pid, &status, 0) < 0)
                throw qpid::Exception(QPID_MSG("Waiting for process " << pid << ": " << qpid::strError(errno)));
            if (WEXITSTATUS(status) != 0)
                throw qpid::Exception(QPID_MSG("Process " << pid << " exited with status: " << WEXITSTATUS(status)));
        }
    }

    void parent(pid_t pid_) {
        pid = pid_;
        qpid::log::Logger::instance().setPrefix("parent");
        std::string portStr = wait(5);
        port = boost::lexical_cast<uint16_t>(portStr);
    }

    void child() {
        prefix += boost::lexical_cast<std::string>(long(getpid()));
        qpid::log::Logger::instance().setPrefix(prefix);
        opts.port = 0;
        boost::shared_ptr<qpid::broker::Broker> broker(new qpid::broker::Broker(opts));
        qpid::broker::SignalHandler::setBroker(broker);
        QPID_LOG(info, "ForkedBroker started on " << broker->getPort());
        ready(boost::lexical_cast<std::string>(broker->getPort())); // Notify parent.
        broker->run();
        QPID_LOG(notice, "ForkedBroker exiting.");

        // Force exit in the child process, otherwise we will try to
        // carry with parent tests.
        broker.reset();         // Run broker dtor before we exit.
        exit(0);
    }

    uint16_t getPort() { return port; }
};

#endif  /*!TESTS_FORKEDBROKER_H*/
