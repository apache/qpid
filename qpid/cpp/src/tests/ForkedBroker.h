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
#include "qpid/log/Statement.h"
#include "qpid/broker/Broker.h"
#include <boost/lexical_cast.hpp>
#include <string>
#include <stdio.h>
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
class ForkedBroker {
  public:
    ForkedBroker(std::vector<const char*> argv) { init(argv); }

    ForkedBroker(int argc, const char* const argv[]) {
        std::vector<const char*> args(argv, argv+argc);
        init(args);
    }

    ~ForkedBroker() {
        try { kill(); } catch(const std::exception& e) {
            QPID_LOG(error, QPID_MSG("Killing forked broker: " << e.what()));
        }
    }

    void kill(int sig=SIGINT) {
        if (pid == 0) return;
        int savePid = pid;      
        pid = 0;                // Reset pid here in case of an exception.
        using qpid::ErrnoException;
        if (::kill(savePid, sig) < 0) 
            throw ErrnoException("kill failed");
        int status;
        if (::waitpid(savePid, &status, 0) < 0) 
            throw ErrnoException("wait for forked process failed");
        if (WEXITSTATUS(status) != 0) 
            throw qpid::Exception(QPID_MSG("Forked broker exited with: " << WEXITSTATUS(status)));
    }

    uint16_t getPort() { return port; }
    pid_t getPID() { return pid; }

  private:

    template <class F> struct OnExit {
        F fn;
        OnExit(F f) : fn(f) {}
        ~OnExit()  { fn(); }
    };
        
    void init(const std::vector<const char*>& args) {
        using qpid::ErrnoException;
        port = 0;
        int pipeFds[2];
        if(::pipe(pipeFds) < 0) throw ErrnoException("Can't create pipe");
        pid = ::fork();
        if (pid < 0) throw ErrnoException("Fork failed");
        if (pid) {              // parent
            ::close(pipeFds[1]);
            FILE* f = ::fdopen(pipeFds[0], "r");
            if (!f) throw ErrnoException("fopen failed");
            if (::fscanf(f, "%d", &port) != 1) {
                if (ferror(f)) throw ErrnoException("Error reading port number from child.");
                else throw qpid::Exception("EOF reading port number from child.");
            }
            ::close(pipeFds[0]);
        }
        else {                  // child
            ::close(pipeFds[0]);
            int fd = ::dup2(pipeFds[1], 1); // pipe stdout to the parent.
            if (fd < 0) throw ErrnoException("dup2 failed");
            const char* prog = "../qpidd";
            std::vector<const char*> args2(args);
            args2.push_back("--port=0");
            args2.push_back("--mgmt-enable=no"); // TODO aconway 2008-07-16: why does mgmt cause problems?
            if (!::getenv("QPID_TRACE") && !::getenv("QPID_LOG_ENABLE"))
            args2.push_back("--log-enable=error+"); // Keep quiet except for errors.
            args2.push_back(0);
            execv(prog, const_cast<char* const*>(&args2[0]));
            throw ErrnoException("execv failed");
        }
    }

    pid_t pid;
    int port;
};

#endif  /*!TESTS_FORKEDBROKER_H*/
