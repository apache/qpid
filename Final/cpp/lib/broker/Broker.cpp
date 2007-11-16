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
#include <iostream>
#include <memory>
#include <Broker.h>

using namespace qpid::broker;
using namespace qpid::sys;

Broker::Options::Options() :
    workerThreads(5),
    maxConnections(500),
    connectionBacklog(10),
    store(),
    stagingThreshold(5000000)
{}

void Broker::Options::addTo(po::options_description& desc)
{
    using namespace po;
    CommonOptions::addTo(desc);
    desc.add_options()
        ("worker-threads", optValue(workerThreads, "N"),
         "Broker thread pool size")
        ("max-connections", optValue(maxConnections, "N"),
         "Maximum allowed connections")
        ("connection-backlog", optValue(connectionBacklog, "N"),
         "Connection backlog limit for server socket.")
        ("staging-threshold", optValue(stagingThreshold, "N"),
         "Messages over N bytes are staged to disk.")
        ("store", optValue(store,"LIBNAME"),
         "Name of message store shared library.");
}


Broker::Broker(const Options& config) :
    acceptor(Acceptor::create(config.port,
                              config.connectionBacklog,
                              config.workerThreads,
                              config.trace)),
    factory(config.store)
{ }


Broker::shared_ptr Broker::create(int16_t port) 
{
    Options config;
    config.port=port;
    return create(config);
}

Broker::shared_ptr Broker::create(const Options& config) {
    return Broker::shared_ptr(new Broker(config));
}    

void Broker::run() {
    acceptor->run(&factory);
}

void Broker::shutdown() {
    if (acceptor)
        acceptor->shutdown();
}

Broker::~Broker() { }

