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
 * \file rfc.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::rfc (rotating
 * file controller). See comments in file rfc.h for details.
 *
 * \author Kim van der Riet
 */

#include "qpid/legacystore/jrnl/rfc.h"

#include <cassert>

namespace mrg
{
namespace journal
{

rfc::rfc(const lpmgr* lpmp): _lpmp(lpmp), _fc_index(0), _curr_fc(0)
{}

rfc::~rfc()
{}

void
rfc::finalize()
{
    unset_findex();
}

void
rfc::set_findex(const u_int16_t fc_index)
{
    _fc_index = fc_index;
    _curr_fc = _lpmp->get_fcntlp(fc_index);
    _curr_fc->rd_reset();
}

void
rfc::unset_findex()
{
    _fc_index = 0;
    _curr_fc = 0;
}

std::string
rfc::status_str() const
{
    if (!_lpmp->is_init())
        return "state: Uninitialized";
    if (_curr_fc == 0)
        return "state: Inactive";
    std::ostringstream oss;
    oss << "state: Active";
    return oss.str();
}

} // namespace journal
} // namespace mrg
