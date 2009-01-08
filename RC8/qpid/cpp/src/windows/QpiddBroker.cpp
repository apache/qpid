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

#include "qpidd.h"
#include "qpid/Exception.h"
#include "qpid/Options.h"
#include "qpid/Plugin.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/sys/windows/check.h"
#include "qpid/broker/Broker.h"

#include <iostream>

// These need to be made something sensible, like reading a value from
// the registry. But for now, get things going with a local definition.
namespace {
const char *CONF_FILE = "qpid_broker.conf";
const char *MODULE_DIR = ".";
}

using namespace qpid::broker;

BootstrapOptions::BootstrapOptions(const char* argv0)
  : qpid::Options("Options"),
    common("", CONF_FILE),
    module(MODULE_DIR),
    log(argv0)
{
    add(common);
    add(module);
    add(log);
}

struct QpiddWindowsOptions : public QpiddOptionsPrivate {
    QpiddWindowsOptions(QpiddOptions *parent) : QpiddOptionsPrivate(parent) {
    }
};

QpiddOptions::QpiddOptions(const char* argv0)
  : qpid::Options("Options"),
    common("", CONF_FILE),
    module(MODULE_DIR),
    log(argv0)
{
    add(common);
    add(module);
    add(broker);
    add(log);

    platform.reset(new QpiddWindowsOptions(this));
    qpid::Plugin::addOptions(*this);
}

void QpiddOptions::usage() const {
    std::cout << "Usage: qpidd [OPTIONS]" << std::endl << std::endl
              << *this << std::endl;
}

int QpiddBroker::execute (QpiddOptions *options) {
    // Options that affect a running daemon.
    QpiddWindowsOptions *myOptions =
      reinterpret_cast<QpiddWindowsOptions *>(options->platform.get());
    if (myOptions == 0)
        throw qpid::Exception("Internal error obtaining platform options");

    boost::intrusive_ptr<Broker> brokerPtr(new Broker(options->broker));
    if (options->broker.port == 0)
      std::cout << (uint16_t)(brokerPtr->getPort("")) << std::endl; 
    brokerPtr->run();
    return 0;
}
