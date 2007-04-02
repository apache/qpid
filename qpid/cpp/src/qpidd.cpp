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
#include "broker/Broker.h"
#include "broker/Configuration.h"
#include <signal.h>
#include <iostream>
#include <memory>
#include <cerrno>
#include "../config.h"
#include <unistd.h>

static char const* programName = "qpidd";

using namespace qpid::broker;
using namespace qpid::sys;

Broker::shared_ptr broker;

void handle_signal(int /*signal*/){
    std::cerr << "Shutting down..." << std::endl;
    broker->shutdown();
}

int main(int argc, char** argv)
{
    Configuration config;
    try {
        config.parse(programName, argc, argv);

        if(config.isHelp()){
            config.usage();
        }else if(config.isVersion()){
            std::cout << programName << " (" << PACKAGE_NAME << ") version "
                      << PACKAGE_VERSION << std::endl;
        }else{
            broker = Broker::create(config);
            signal(SIGINT, handle_signal);
            if (config.isDaemon()) {
                // Detach & run as daemon.
                int chdirRoot = 0;  // 0 means chdir to root.
                int closeStd = 0;   // 0 means close stdin/out/err
                if (daemon(chdirRoot, closeStd) < 0) {
                    char buf[512];
                    
                    std::cerr << "Failed to detach as daemon: "
                              << strerror_r(errno, buf, sizeof(buf))
                              << std::endl;;
                    return 1;
                }
            }
            broker->run();
        }
        return 0;
    }
    catch (const Configuration::BadOptionException& e) {
        std::cerr << e.what() << std::endl << std::endl;
        config.usage();
    } catch(const std::exception& e) {
        std::cerr << e.what() << std::endl;
    }
    return 1;
}
