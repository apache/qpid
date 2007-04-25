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

#include "CommonOptions.h"
#include <algorithm>

namespace qpid {

namespace program_options {

char env2optchar(char env) {
    return (env=='_') ? '-' : tolower(env);
}
    
const std::string envPrefix("QPID_");

std::string env2option(const std::string& env) {
    if (env.find(envPrefix) ==0) {
        std::string opt = env.substr(envPrefix.size());
        std::transform(opt.begin(), opt.end(), opt.begin(), env2optchar);
        return opt;
    }
    return std::string();
} 

} // namespace program_options

const int CommonOptions::DEFAULT_PORT=5672;

CommonOptions::CommonOptions() : trace(false), port(DEFAULT_PORT) {}

void CommonOptions::addTo(po::options_description& desc)
{
    using namespace po;
    desc.add_options()
        ("trace,t", optValue(trace), "Enable debug tracing" )
        ("port,p", optValue(port,"PORT"), "Use PORT for AMQP connections.");
}

} // namespace qpid

