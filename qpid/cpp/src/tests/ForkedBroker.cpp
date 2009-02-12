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

#include "ForkedBroker.h"
#include <boost/bind.hpp>
#include <algorithm>

ForkedBroker::ForkedBroker(const Args& args) { init(args); }

ForkedBroker::ForkedBroker(int argc, const char* const argv[]) { init(Args(argv, argc+argv)); }

ForkedBroker::~ForkedBroker() {
    try { kill(); } catch(const std::exception& e) {
        QPID_LOG(error, QPID_MSG("Killing forked broker: " << e.what()));
    }
}

void ForkedBroker::kill(int sig) {
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

        
void ForkedBroker::init(const Args& userArgs) {
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
        // FIXME aconway 2009-02-12: 
        int fd = ::dup2(pipeFds[1], 1); // pipe stdout to the parent.
        if (fd < 0) throw ErrnoException("dup2 failed");
        const char* prog = "../qpidd";
        Args args(userArgs);
        args.push_back("--port=0");
        if (!::getenv("QPID_TRACE") && !::getenv("QPID_LOG_ENABLE"))
            args.push_back("--log-enable=error+"); // Keep quiet except for errors.
        std::vector<const char*> argv(args.size());
        std::transform(args.begin(), args.end(), argv.begin(), boost::bind(&std::string::c_str, _1));
        argv.push_back(0);
        execv(prog, const_cast<char* const*>(&argv[0]));
        throw ErrnoException("execv failed");
    }
}
