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

#include "qpid/log/Options.h"
#include "qpid/log/SinkOptions.h"
#include "qpid/log/Statement.h"
#include "qpid/Options.h"
#include <map>
#include <string>
#include <algorithm>

namespace qpid {
namespace log {

Options::Options(const std::string& argv0_, const std::string& name_) :
    qpid::Options(name_),
    argv0(argv0_),
    name(name_),
    time(true),
    level(true),
    thread(false),
    source(false),
    function(false),
    hiresTs(false),
    category(true),
    trace(false),
    sinkOptions (SinkOptions::create(argv0_))
{
    selectors.push_back("notice+");

    addOptions()
        ("trace,t", optValue(trace), "Enables all logging" )
        ("log-enable", optValue(selectors, "RULE"),
         ("Enables logging for selected levels and components. "
          "RULE is in the form 'LEVEL[+-][:PATTERN]'\n"
          "LEVEL is one of: \n\t "+getLevels()+"\n"
          "PATTERN is a logging category name, or a namespace-qualified "
          "function name or name fragment. "
          "Logging category names are: \n\t "+getCategories()+"\n"
          "For example:\n"
          "\t'--log-enable warning+'\n"
          "logs all warning, error and critical messages.\n"
          "\t'--log-enable trace+:Broker'\n"
          "logs all category 'Broker' messages.\n"
          "\t'--log-enable debug:framing'\n"
          "logs debug messages from all functions with 'framing' in the namespace or function name.\n"
          "This option can be used multiple times").c_str())
        ("log-disable", optValue(deselectors, "RULE"),
         ("Disables logging for selected levels and components. "
          "RULE is in the form 'LEVEL[+-][:PATTERN]'\n"
          "LEVEL is one of: \n\t "+getLevels()+"\n"
          "PATTERN is a logging category name, or a namespace-qualified "
          "function name or name fragment. "
          "Logging category names are: \n\t "+getCategories()+"\n"
          "For example:\n"
          "\t'--log-disable warning-'\n"
          "disables logging all warning, notice, info, debug, and trace messages.\n"
          "\t'--log-disable trace:Broker'\n"
          "disables all category 'Broker' trace messages.\n"
          "\t'--log-disable debug-:qmf::'\n"
          "disables logging debug and trace messages from all functions with 'qmf::' in the namespace.\n"
          "This option can be used multiple times").c_str())
        ("log-time", optValue(time, "yes|no"), "Include time in log messages")
        ("log-level", optValue(level,"yes|no"), "Include severity level in log messages")
        ("log-source", optValue(source,"yes|no"), "Include source file:line in log messages")
        ("log-thread", optValue(thread,"yes|no"), "Include thread ID in log messages")
        ("log-function", optValue(function,"yes|no"), "Include function signature in log messages")
        ("log-hires-timestamp", optValue(hiresTs,"yes|no"), "Use hi-resolution timestamps in log messages")
        ("log-category", optValue(category,"yes|no"), "Include category in log messages")
        ("log-prefix", optValue(prefix,"STRING"), "Prefix to prepend to all log messages")
        ;
    add(*sinkOptions);
}

Options::Options(const Options &o) :
    qpid::Options(o.name),
    argv0(o.argv0),
    name(o.name),
    selectors(o.selectors),
    deselectors(o.deselectors),
    time(o.time),
    level(o.level),
    thread(o.thread),
    source(o.source),
    function(o.function),
    hiresTs(o.hiresTs),
    category(o.category),
    trace(o.trace),
    prefix(o.prefix),
    sinkOptions (SinkOptions::create(o.argv0))
{
    *sinkOptions = *o.sinkOptions;
}

Options& Options::operator=(const Options& x) {
    if (this != &x) {
        argv0 = x.argv0;
        name = x.name;
        selectors = x.selectors;
        deselectors = x.deselectors;
        time = x.time;
        level= x.level;
        thread = x.thread;
        source = x.source;
        function = x.function;
        hiresTs = x.hiresTs;
        category = x.category;
        trace = x.trace;
        prefix = x.prefix;
        *sinkOptions = *x.sinkOptions;
    }
    return *this;
}

std::string getLevels()
{
    std::ostringstream levels;
    levels << LevelTraits::name(Level(0));
    for (int i = 1; i < LevelTraits::COUNT; ++i)
        levels << " " << LevelTraits::name(Level(i));
    return levels.str();
}

std::string getCategories()
{
    std::ostringstream categories;
    categories << CategoryTraits::name(Category(0));
    for (int i = 1; i < CategoryTraits::COUNT; ++i)
        categories << " " << CategoryTraits::name(Category(i));
    return categories.str();
}

}} // namespace qpid::log
