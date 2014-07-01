#ifndef QPID_H
#define QPID_H

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

#include "qpid/Modules.h"
#include "qpid/Options.h"
#include "qpid/broker/BrokerOptions.h"
#include "qpid/log/Options.h"

#include <memory>

namespace qpid {
namespace broker {

// BootstrapOptions is a minimal subset of options used for a pre-parse
// of the command line to discover which plugin modules need to be loaded.
// The pre-parse is necessary because plugin modules may supply their own
// set of options.  CommonOptions is needed to properly support loading
// from a configuration file.
struct BootstrapOptions : public qpid::Options {
    qpid::CommonOptions common;
    qpid::ModuleOptions module;
    qpid::log::Options log;

    BootstrapOptions(const char *argv0);
    void usage() const;
};

// Each platform derives an options struct from QpiddOptionsPrivate, adding
// platform-specific option types. QpiddOptions needs to allocation one of
// these derived structs from its constructor.
struct QpiddOptions;
struct QpiddOptionsPrivate {
    QpiddOptions *options;
    QpiddOptionsPrivate(QpiddOptions *parent) : options(parent) {}
    virtual ~QpiddOptionsPrivate() {}
protected:
    QpiddOptionsPrivate() {}
};

struct QpiddOptions : public qpid::Options {
    qpid::CommonOptions common;
    qpid::ModuleOptions module;
    qpid::broker::BrokerOptions broker;
    qpid::log::Options log;
    std::auto_ptr<QpiddOptionsPrivate> platform;

    QpiddOptions(const char *argv0);
    void usage() const;
};

class QpiddBroker {
public:
    int execute (QpiddOptions *options);
};

// Broker real entry; various system-invoked entrypoints call here.
int run_broker(int argc, char *argv[], bool hidden = false);

}}
#endif  /*!QPID_H*/
