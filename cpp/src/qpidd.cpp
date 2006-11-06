/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "qpid/broker/Broker.h"
#include "qpid/broker/Configuration.h"
#include "apr-1/apr_signal.h"
#include <iostream>
#include <memory>

using namespace qpid::broker;
using namespace qpid::sys;

void handle_signal(int /*signal*/){
    std::cout << "Shutting down..." << std::endl;
}

int main(int argc, char** argv)
{
    Configuration config;
    try {
        config.parse(argc, argv);
        if(config.isHelp()){
            config.usage();
        }else{
            apr_signal(SIGINT, handle_signal);
            Broker::shared_ptr broker = Broker::create(config);
            broker->run();
        }
        return 0;
    } catch(const std::exception& e) {
        std::cout << e.what() << std::endl;
    }
    return 1;
}
