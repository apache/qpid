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

#include "Options.h"
#include "qpid/Exception.h"
#include <fstream>
#include <algorithm>
#include <iostream>

namespace qpid {

using namespace std;

namespace {

char env2optchar(char env) { return (env=='_') ? '-' : tolower(env); }

struct Mapper {
    Mapper(const Options& o) : opts(o) {}
    string operator()(const string& env) {
        static const std::string prefix("QPID_");
        if (env.substr(0, prefix.size()) == prefix) {
            string opt = env.substr(prefix.size());
            transform(opt.begin(), opt.end(), opt.begin(), env2optchar);
            // Ignore env vars that don't match to known options.
            if (opts.find_nothrow(opt, false))
                return opt;
        }
        return string();
    }
    const Options& opts;
};

}
std::string prettyArg(const std::string& name, const std::string& value) {
    return value.empty() ? name : name+" (="+value+")";
}

Options::Options(const string& name) : po::options_description(name) {}

void Options::parse(int argc, char** argv, const std::string& configFile)
{
    string parsing;
    try {
        po::variables_map vm;
        parsing="command line options";
        if (argc > 0 && argv != 0)
            po::store(po::parse_command_line(argc, argv, *this), vm);
        parsing="environment variables";
        po::store(po::parse_environment(*this, Mapper(*this)), vm);
        po::notify(vm); // configFile may be updated from arg/env options.
        if (!configFile.empty()) {
            parsing="configuration file "+configFile;
            ifstream conf(configFile.c_str());
            if (conf.good()) {
                conf.exceptions(ifstream::failbit|ifstream::badbit);
                po::store(po::parse_config_file(conf, *this), vm);
            }
        }
        po::notify(vm);
    }
    catch (const std::exception& e) {
        ostringstream msg;
        msg << "Error in " << parsing << ": " << e.what() << endl;
        if (find_nothrow("help", false))
            msg << "Use --help to see valid options" << endl;
        throw Exception(msg.str());
    }
}

CommonOptions::CommonOptions(const string& name) : Options(name) {
    addOptions()
        ("help,h", optValue(help), "Print help message.")
        ("version,v", optValue(version), "Print version information.")
        ("config", optValue(config, "FILE"), "Configuation file.");
}

} // namespace qpid

