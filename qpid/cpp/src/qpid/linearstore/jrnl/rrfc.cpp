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
 * \file rrfc.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::rrfc (rotating
 * file controller). See comments in file rrfc.h for details.
 *
 * \author Kim van der Riet
 */


#include "qpid/legacystore/jrnl/rrfc.h"

#include <cerrno>
#include <fcntl.h>
#include <unistd.h>
#include "qpid/legacystore/jrnl/jerrno.h"
#include "qpid/legacystore/jrnl/jexception.h"

namespace mrg
{
namespace journal
{

rrfc::rrfc(const lpmgr* lpmp): rfc(lpmp), _fh(-1), _valid(false)
{}

rrfc::~rrfc()
{
    close_fh();
}

void
rrfc::finalize()
{
    unset_findex();
    rfc::finalize();
}

void
rrfc::set_findex(const u_int16_t fc_index)
{
    rfc::set_findex(fc_index);
    open_fh(_curr_fc->fname());
}

void
rrfc::unset_findex()
{
    set_invalid();
    close_fh();
    rfc::unset_findex();
}

iores
rrfc::rotate()
{
    if (!_lpmp->num_jfiles())
        throw jexception(jerrno::JERR__NINIT, "rrfc", "rotate");
    u_int16_t next_fc_index = _fc_index + 1;
    if (next_fc_index == _lpmp->num_jfiles())
        next_fc_index = 0;
    set_findex(next_fc_index);
    return RHM_IORES_SUCCESS;
}

std::string
rrfc::status_str() const
{
    std::ostringstream oss;
    oss << "rrfc: " << rfc::status_str();
    if (is_active())
        oss << " fcntl[" << _fc_index << "]: " << _curr_fc->status_str();
    return oss.str();
}

// === protected functions ===

void
rrfc::open_fh(const std::string& fn)
{
    close_fh();
    _fh = ::open(fn.c_str(), O_RDONLY | O_DIRECT);
    if (_fh < 0)
    {
        std::ostringstream oss;
        oss << "file=\"" << fn << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_RRFC_OPENRD, oss.str(), "rrfc", "open_fh");
    }
}

void
rrfc::close_fh()
{
    if (_fh >= 0)
    {
        ::close(_fh);
        _fh = -1;
    }
}

} // namespace journal
} // namespace mrg
