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

#include "read_arg.h"

#include <cassert>
#include <boost/program_options.hpp>
namespace po = boost::program_options;

namespace mrg
{
namespace jtt
{
std::map<std::string, read_arg::read_mode_t> read_arg::_map;
std::string read_arg::_description;
const bool read_arg::init = __init();

// static init fn
bool
read_arg::__init()
{
    // Set string versions of each enum option here
    _map["NONE"] = NONE;
    _map["ALL"] = ALL;
    _map["RANDOM"] = RANDOM;
    _map["LAZYLOAD"] = LAZYLOAD;
    _description = "Determines if and when messages will be read prior to dequeueing. "
            "Values: (NONE | ALL | RANDOM | LAZYLOAD)";
    return true;
}

void
read_arg::parse(const std::string& str)
{
    std::map<std::string, read_arg::read_mode_t>::const_iterator i = _map.find(str);
    if (i == _map.end())
        throw po::invalid_option_value(str);
    _rm = i->second;
}

// static fn
const std::string&
read_arg::str(const read_mode_t rm)
{
    std::map<std::string, read_mode_t>::const_iterator i = _map.begin();
    while (i->second != rm && i != _map.end()) i++;
    assert(i != _map.end());
    return i->first;
}

// static fn
const std::string&
read_arg::descr()
{
    return _description;
}

std::ostream&
operator<<(std::ostream& os, const read_arg& ra)
{
    os << ra.str();
    return os;
}

std::istream&
operator>>(std::istream& is, read_arg& ra)
{
    std::string s;
    is >> s;
    ra.parse(s);
    return is;
}

} // namespace jtt
} // namespace mrg
