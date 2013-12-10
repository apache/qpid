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
 * \file lpmgr.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::lpmgr (non-logging file
 * handle), used for controlling journal log files. See comments in file
 * lpmgr.h for details.
 *
 * \author Kim van der Riet
 */

#include "qpid/legacystore/jrnl/lpmgr.h"

#include <cassert>
#include <qpid/legacystore/jrnl/jerrno.h>
#include <qpid/legacystore/jrnl/jexception.h>

namespace mrg
{
namespace journal
{

lpmgr::lpmgr() : _ae(false), _ae_max_jfiles(0)
{}

lpmgr::~lpmgr()
{
    finalize();
}

void
lpmgr::initialize(const u_int16_t num_jfiles,
                  const bool ae,
                  const u_int16_t ae_max_jfiles,
                  jcntl* const jcp,
                  new_obj_fn_ptr fp)
{
    assert(jcp != 0);
    finalize();

    // Validate params
    if (ae && ae_max_jfiles > 0 && ae_max_jfiles <= num_jfiles)
    {
        std::ostringstream oss;
        oss << "ae_max_jfiles (" << ae_max_jfiles << ") <= num_jfiles (" << num_jfiles << ")";
        throw jexception(jerrno::JERR_LFMGR_BADAEFNUMLIM,  oss.str(), "lpmgr", "initialize");
    }
    _ae = ae;
    _ae_max_jfiles = ae_max_jfiles;

    const std::size_t num_res_files = ae
                                      ? (ae_max_jfiles ? ae_max_jfiles : JRNL_MAX_NUM_FILES)
                                      : num_jfiles;
    _fcntl_arr.reserve(num_res_files);
    append(jcp, fp, num_jfiles);
}

void
lpmgr::recover(const rcvdat& rd,
               jcntl* const jcp,
               new_obj_fn_ptr fp)
{
    assert(jcp != 0);
    finalize();

    // Validate rd params
    if (rd._aemjf > 0 && rd._aemjf <= rd._njf)
    {
        std::ostringstream oss;
        oss << "ae_max_jfiles (" << rd._aemjf << ") <= num_jfiles (" << rd._njf << ")";
        throw jexception(jerrno::JERR_LFMGR_BADAEFNUMLIM,  oss.str(), "lpmgr", "recover");
    }
    _ae = rd._ae;
    _ae_max_jfiles = rd._aemjf;

    const std::size_t num_res_files = rd._ae
                                      ? (rd._aemjf ? rd._aemjf : JRNL_MAX_NUM_FILES)
                                      : rd._njf;
    _fcntl_arr.reserve(num_res_files);
    _fcntl_arr.assign(rd._njf, 0);
    std::vector<u_int16_t> lfid_list(rd._fid_list.size(), 0);
    for (std::size_t lid = 0; lid < rd._fid_list.size(); lid++)
        lfid_list[rd._fid_list[lid]] = lid;
    // NOTE: rd._fid_list may be smaller than rd._njf (journal may be empty or not yet file-cycled)
    for (std::size_t pfid = 0; pfid < rd._njf; pfid++)
        if (pfid < rd._fid_list.size())
            _fcntl_arr[lfid_list[pfid]] = fp(jcp, lfid_list[pfid], pfid, &rd);
        else
            _fcntl_arr[pfid] = fp(jcp, pfid, pfid, &rd);
}

void
lpmgr::insert(const u_int16_t after_lfid,
              jcntl* const jcp,
              new_obj_fn_ptr fp,
              const u_int16_t num_jfiles)
{
    assert(jcp != 0);
    assert(after_lfid < _fcntl_arr.size());
    if (!_ae) throw jexception(jerrno::JERR_LFMGR_AEDISABLED, "lpmgr", "insert");
    if (num_jfiles == 0) return;
    std::size_t pfid = _fcntl_arr.size();
    const u_int16_t eff_ae_max_jfiles = _ae_max_jfiles ? _ae_max_jfiles : JRNL_MAX_NUM_FILES;
    if (pfid + num_jfiles > eff_ae_max_jfiles)
    {
        std::ostringstream oss;
        oss << "num_files=" << pfid << " incr=" << num_jfiles << " limit=" << _ae_max_jfiles;
        throw jexception(jerrno::JERR_LFMGR_AEFNUMLIMIT, oss.str(), "lpmgr", "insert");
    }
    for (std::size_t lid = after_lfid + 1; lid <= after_lfid + num_jfiles; lid++, pfid++)
        _fcntl_arr.insert(_fcntl_arr.begin() + lid, fp(jcp, lid, pfid, 0));
    for (std::size_t lid = after_lfid + num_jfiles + 1; lid < _fcntl_arr.size(); lid++)
    {
        fcntl* p = _fcntl_arr[lid];
        assert(p != 0);
        p->set_lfid(p->lfid() + num_jfiles);
    }
}

void
lpmgr::finalize()
{
    for (u_int32_t i = 0; i < _fcntl_arr.size(); i++)
        delete _fcntl_arr[i];
    _fcntl_arr.clear();
    _ae = false;
    _ae_max_jfiles = 0;
}

void
lpmgr::set_ae(const bool ae)
{
    if (ae && _ae_max_jfiles > 0 && _ae_max_jfiles <= _fcntl_arr.size())
    {
        std::ostringstream oss;
        oss << "ae_max_jfiles (" << _ae_max_jfiles << ") <= _fcntl_arr.size (" << _fcntl_arr.size() << ")";
        throw jexception(jerrno::JERR_LFMGR_BADAEFNUMLIM, oss.str(), "lpmgr", "set_ae");
    }
    if (ae && _fcntl_arr.max_size() < _ae_max_jfiles)
        _fcntl_arr.reserve(_ae_max_jfiles ? _ae_max_jfiles : JRNL_MAX_NUM_FILES);
    _ae = ae;
}

void
lpmgr::set_ae_max_jfiles(const u_int16_t ae_max_jfiles)
{
    if (_ae && ae_max_jfiles > 0 && ae_max_jfiles <= _fcntl_arr.size())
    {
        std::ostringstream oss;
        oss << "ae_max_jfiles (" << _ae_max_jfiles << ") <= _fcntl_arr.size() (" << _fcntl_arr.size() << ")";
        throw jexception(jerrno::JERR_LFMGR_BADAEFNUMLIM,  oss.str(), "lpmgr", "set_ae_max_jfiles");
    }
    if (_ae && _fcntl_arr.max_size() < ae_max_jfiles)
        _fcntl_arr.reserve(ae_max_jfiles ? ae_max_jfiles : JRNL_MAX_NUM_FILES);
    _ae_max_jfiles = ae_max_jfiles;
}

u_int16_t
lpmgr::ae_jfiles_rem() const
{
    if (_ae_max_jfiles > _fcntl_arr.size()) return _ae_max_jfiles - _fcntl_arr.size();
    if (_ae_max_jfiles == 0) return JRNL_MAX_NUM_FILES - _fcntl_arr.size();
    return 0;
}

// Testing functions

void
lpmgr::get_pfid_list(std::vector<u_int16_t>& pfid_list) const
{
    pfid_list.clear();
    for (std::size_t i = 0; i < _fcntl_arr.size(); i++)
        pfid_list.push_back(_fcntl_arr[i]->pfid());
}

void
lpmgr::get_lfid_list(std::vector<u_int16_t>& lfid_list) const
{
    lfid_list.clear();
    lfid_list.assign(_fcntl_arr.size(), 0);
    for (std::size_t i = 0; i < _fcntl_arr.size(); i++)
        lfid_list[_fcntl_arr[i]->pfid()] = i;
}

// === protected fns ===

void
lpmgr::append(jcntl* const jcp,
              new_obj_fn_ptr fp,
              const u_int16_t num_jfiles)
{
    std::size_t s = _fcntl_arr.size();
    if (_ae_max_jfiles && s + num_jfiles > _ae_max_jfiles)
    {
        std::ostringstream oss;
        oss << "num_files=" << s << " incr=" << num_jfiles << " limit=" << _ae_max_jfiles;
        throw jexception(jerrno::JERR_LFMGR_AEFNUMLIMIT, oss.str(), "lpmgr", "append");
    }
    for (std::size_t i = s; i < s + num_jfiles; i++)
        _fcntl_arr.push_back(fp(jcp, i, i, 0));
}

} // namespace journal
} // namespace mrg
