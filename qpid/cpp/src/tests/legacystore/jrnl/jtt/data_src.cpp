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

#include "data_src.h"

#include <cstddef>
#include <iomanip>
#include <sstream>

namespace mrg
{
namespace jtt
{

char data_src::_data_src[data_src::max_dsize];
char data_src::_xid_src[data_src::max_xsize];
bool data_src::_initialized = data_src::__init();
u_int64_t data_src::_xid_cnt = 0ULL;
mrg::journal::smutex data_src::_sm;

data_src::data_src()
{}

bool
data_src::__init()
{
    for (unsigned i=0; i<max_dsize; i++)
        _data_src[i] = '0' + ((i + 1) % 10); // 123456789012345...
    for (unsigned j=0; j<max_xsize; j++)
        _xid_src[j] = 'a' + (j % 26);        // abc...xyzabc...
    return true;
}

const char*
data_src::get_data(const std::size_t offs)
{
    if (offs >= max_dsize) return 0;
    return _data_src + offs;
}

std::string
data_src::get_xid(const std::size_t xid_size)
{
    if (xid_size == 0)
        return "";
    std::ostringstream oss;
    oss << std::setfill('0');
    if (xid_size < 9)
        oss << std::setw(xid_size) << get_xid_cnt();
    else if (xid_size < 13)
        oss << "xid:" << std::setw(xid_size - 4) << get_xid_cnt();
    else
    {
        oss << "xid:" << std::setw(8) << get_xid_cnt() << ":";
        oss.write(get_xid_content(13), xid_size - 13);
    }
    return oss.str();
}

const char*
data_src::get_xid_content(const std::size_t offs)
{
    if (offs >= max_xsize) return 0;
    return _xid_src + offs;
}

} // namespace jtt
} // namespace mrg

