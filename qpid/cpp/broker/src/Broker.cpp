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
#include <iostream>
#include <memory>
#include "Broker.h"
#include "Acceptor.h"
#include "Configuration.h"
#include "QpidError.h"
#include "SessionHandlerFactoryImpl.h"
#include "BlockingAPRAcceptor.h"
#include "LFAcceptor.h"


using namespace qpid::broker;
using namespace qpid::io;

namespace {
    Acceptor* createAcceptor(const Configuration& config){
        const string type(config.getAcceptor());
        if("blocking" == type){
            std::cout << "Using blocking acceptor " << std::endl;
            return new BlockingAPRAcceptor(config.isTrace(), config.getConnectionBacklog());
        }else if("non-blocking" == type){
            std::cout << "Using non-blocking acceptor " << std::endl;
            return new LFAcceptor(config.isTrace(), 
                                  config.getConnectionBacklog(), 
                                  config.getWorkerThreads(),
                                  config.getMaxConnections());
        }
        throw Configuration::ParseException("Unrecognised acceptor: " + type);
    }
}

Broker::Broker(const Configuration& config) :
    acceptor(createAcceptor(config)),
    port(config.getPort()),
    isBound(false) {}

Broker::shared_ptr Broker::create(int port) 
{
    Configuration config;
    config.setPort(port);
    return create(config);
}

Broker::shared_ptr Broker::create(const Configuration& config) {
    return Broker::shared_ptr(new Broker(config));
}    
        
int16_t Broker::bind()
{
    if (!isBound) {
        port = acceptor->bind(port);
    }
    return port;
}

void Broker::run() {
    bind();
    acceptor->run(&factory);
}

void Broker::shutdown() {
    acceptor->shutdown();
}

Broker::~Broker() { }

const int16_t Broker::DEFAULT_PORT(5672);
