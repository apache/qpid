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

#include "./qpidd.h"
#include "qpid/Plugin.h"
#include "qpid/Version.h"
#include "qpid/log/Logger.h"
#include "qpid/log/Statement.h"

#include <iostream>
#include <memory>
using namespace std;

namespace qpid {
namespace broker {

auto_ptr<QpiddOptions> options;

// Broker real entry; various system-invoked entrypoints call here.
int run_broker(int argc, char *argv[], bool hidden)
{
    try
    {
        BootstrapOptions bootOptions(argv[0]);
        string           defaultPath (bootOptions.module.loadDir);

        // --version causes print and exit
        if (bootOptions.findArg(argc, argv, "version")) {
            cout << "qpidd (" << qpid::product << ") version "
                << qpid::version << endl;
            return 0;
        }

        // --help sets a flag so that its presence is known despite
        // subsequent parse problems.
        bool helpArgSeen = bootOptions.findArg(argc, argv, "help");

        // Parse only the common, load, and log options to see which
        // modules need to be loaded.  Once the modules are loaded,
        // the command line will be re-parsed with all of the
        // module-supplied options.
        try {
            bootOptions.parse (argc, argv, bootOptions.common.config, true);
            if (hidden)
                bootOptions.log.sinkOptions->detached();
            qpid::log::Logger::instance().configure(bootOptions.log);
        } catch (const std::exception& e) {
            if (helpArgSeen) {
                // provide help even when parsing fails
                 bootOptions.usage();
            }
            // Couldn't configure logging so write the message direct to stderr.
            cerr << endl << "Unexpected error: " << e.what() << endl;
            return 1;
        }

        for (vector<string>::iterator iter = bootOptions.module.load.begin();
             iter != bootOptions.module.load.end();
             iter++)
            qpid::tryShlib (*iter);

        if (!bootOptions.module.noLoad) {
            bool isDefault = defaultPath == bootOptions.module.loadDir;
            qpid::loadModuleDir (bootOptions.module.loadDir, isDefault);
        }

        // Parse options.  In the second pass, do not allow unknown options.
        // All the modules have been added now, so any unknown options
        // should be flagged as errors.
        try {
            options.reset(new QpiddOptions(argv[0]));
            options->parse(argc, argv, options->common.config, false);
        } catch (const std::exception& /*e*/) {
            if (helpArgSeen) {
                 // provide help even when parsing fails
                options->usage();
            }
            throw;
        }

        // Options that just print information.
        if (helpArgSeen) {
            options->usage();
            return 0;
        }

        // Everything else is driven by the platform-specific broker
        // logic.
        QpiddBroker broker;
        return broker.execute(options.get());
    }
    catch(const exception& e) {
        QPID_LOG(critical, "Unexpected error: " << e.what());
    }
    return 1;
}
}}
