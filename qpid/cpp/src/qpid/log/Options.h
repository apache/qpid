#ifndef OPTIONS_H
#define OPTIONS_H

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
#include "qpid/Options.h"
#include <iosfwd>

namespace qpid {
namespace log {

/** Provides << and >> operators to convert syslog facility values to/from strings. */
struct SyslogFacility {
    int value;
    SyslogFacility(int i=0) : value(i) {}
};

std::ostream& operator<<(std::ostream&, const SyslogFacility&);
std::istream& operator>>(std::istream&, SyslogFacility&);

/** Logging options for config parser. */
struct Options : public qpid::Options {
    /** Pass argv[0] for use in syslog output */
    Options(const std::string& argv0,
            const std::string& name="Logging options");

    std::vector<std::string> selectors;
    std::vector<std::string> outputs;
    bool time, level, thread, source, function;
    bool trace;
    std::string syslogName;
    SyslogFacility syslogFacility;
};


}} // namespace qpid::log



#endif  /*!OPTIONS_H*/
