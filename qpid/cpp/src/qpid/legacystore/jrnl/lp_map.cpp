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

/**
 * \file lp_map.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::lp_map (logical file map). See
 * comments in file lp_map.h for details.
 *
 * \author Kim van der Riet
 */

#include "qpid/legacystore/jrnl/lp_map.h"

#include "qpid/legacystore/jrnl/jerrno.h"
#include "qpid/legacystore/jrnl/jexception.h"
#include <sstream>

namespace mrg
{
namespace journal
{
lp_map::lp_map() : _map() {}
lp_map::~lp_map() {}

void
lp_map::insert(u_int16_t lfid, u_int16_t pfid)
{
    lfpair ip = lfpair(lfid, pfid);
    lfret ret = _map.insert(ip);
    if (ret.second == false)
    {
        std::ostringstream oss;
        oss << std::hex << "lfid=0x" << lfid << " pfid=0x" << pfid;
        throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "lp_map", "insert");
    }
}

void
lp_map::get_pfid_list(std::vector<u_int16_t>& pfid_list)
{
    for (lp_map_citr_t i = _map.begin(); i != _map.end(); i++)
        pfid_list.push_back(i->second);
}

// debug aid
std::string
lp_map::to_string()
{
    std::ostringstream oss;
    oss << "{lfid:pfid ";
    for (lp_map_citr_t i=_map.begin(); i!=_map.end(); i++)
    {
        if (i != _map.begin()) oss << ", ";
        oss << (*i).first << ":" << (*i).second;
    }
    oss << "}";
    return oss.str();
}

} // namespace journal
} // namespace mrg
