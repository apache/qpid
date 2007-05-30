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
#include "Statement.h"
#include "qpid/CommonOptions.h"

namespace qpid {
namespace log {

using namespace std;

Options::Options() :
    time(true), level(true), thread(false), source(false), function(false)
{
    outputs.push_back("stderr");
    selectors.push_back("error+");
}

void Options::addTo(po::options_description& desc) {
    using namespace po;
    ostringstream levels;
    levels << LevelTraits::name(Level(0));
    for (int i = 1; i < LevelTraits::COUNT; ++i)
        levels << " " << LevelTraits::name(Level(i));
    desc.add_options()
        ("log.enable", optValue(selectors, "RULE"),
         "You can specify this option mutliple times.\n"
         "RULE is of the form 'LEVEL[+][:COMPONENT]'"
         "Levels are: trace, debug, info, notice, warning, error, critical."
         "For example:\n"
         "\t'--log.enable warning+' "
         "enables all warning, error and critical messages.\n"
         "\t'--log.enable debug:framing' "
         "enables debug messages from the framing component.")
        ("log.output", optValue(outputs, "FILE"),
         "File to receive log output, or one of these special values: "
         "'stderr', 'stdout', 'syslog'.")
        ("log.time", optValue(time, "yes|no"),
         "Include time in log messages")
        ("log.level", optValue(level,"yes|no"),
         "Include severity level in log messages")
        ("log.source", optValue(source,"yes|no"),
         "Include source file:line in log messages")
        ("log.thread", optValue(thread,"yes|no"),
         "Include thread ID in log messages")
        ("log.function", optValue(function,"yes|no"),
         "Include function signature in log messages");
    
}        
        


}} // namespace qpid::log
