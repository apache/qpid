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

#include "qpid/linearstore/jrnl/enq_map.h"

#include <iomanip>
#include "qpid/linearstore/jrnl/jerrno.h"
#include "qpid/linearstore/jrnl/slock.h"
#include <sstream>


namespace mrg
{
namespace journal
{

// static return/error codes
int16_t enq_map::EMAP_DUP_RID = -3;
int16_t enq_map::EMAP_LOCKED = -2;
int16_t enq_map::EMAP_RID_NOT_FOUND = -1;
int16_t enq_map::EMAP_OK = 0;
int16_t enq_map::EMAP_FALSE = 0;
int16_t enq_map::EMAP_TRUE = 1;

enq_map::enq_map():
        _map(),
        _pfid_enq_cnt()
{}

enq_map::~enq_map() {}

void
enq_map::set_num_jfiles(const uint16_t num_jfiles)
{
    _pfid_enq_cnt.resize(num_jfiles, 0);
}


int16_t
enq_map::insert_pfid(const uint64_t rid, const uint16_t pfid)
{
    return insert_pfid(rid, pfid, false);
}

int16_t
enq_map::insert_pfid(const uint64_t rid, const uint16_t pfid, const bool locked)
{
    std::pair<emap_itr, bool> ret;
    emap_data_struct rec(pfid, locked);
    {
        slock s(_mutex);
        ret = _map.insert(emap_param(rid, rec));
    }
    if (ret.second == false)
        return EMAP_DUP_RID;
    _pfid_enq_cnt.at(pfid)++;
    return EMAP_OK;
}

int16_t
enq_map::get_pfid(const uint64_t rid)
{
    slock s(_mutex);
    emap_itr itr = _map.find(rid);
    if (itr == _map.end()) // not found in map
        return EMAP_RID_NOT_FOUND;
    if (itr->second._lock)
        return EMAP_LOCKED;
    return itr->second._pfid;
}

int16_t
enq_map::get_remove_pfid(const uint64_t rid, const bool txn_flag)
{
    slock s(_mutex);
    emap_itr itr = _map.find(rid);
    if (itr == _map.end()) // not found in map
        return EMAP_RID_NOT_FOUND;
    if (itr->second._lock && !txn_flag) // locked, but not a commit/abort
        return EMAP_LOCKED;
    uint16_t pfid = itr->second._pfid;
    _map.erase(itr);
    _pfid_enq_cnt.at(pfid)--;
    return pfid;
}

bool
enq_map::is_enqueued(const uint64_t rid, bool ignore_lock)
{
    slock s(_mutex);
    emap_itr itr = _map.find(rid);
    if (itr == _map.end()) // not found in map
        return false;
    if (!ignore_lock && itr->second._lock) // locked
        return false;
    return true;
}

int16_t
enq_map::lock(const uint64_t rid)
{
    slock s(_mutex);
    emap_itr itr = _map.find(rid);
    if (itr == _map.end()) // not found in map
        return EMAP_RID_NOT_FOUND;
    itr->second._lock = true;
    return EMAP_OK;
}

int16_t
enq_map::unlock(const uint64_t rid)
{
    slock s(_mutex);
    emap_itr itr = _map.find(rid);
    if (itr == _map.end()) // not found in map
        return EMAP_RID_NOT_FOUND;
    itr->second._lock = false;
    return EMAP_OK;
}

int16_t
enq_map::is_locked(const uint64_t rid)
{
    slock s(_mutex);
    emap_itr itr = _map.find(rid);
    if (itr == _map.end()) // not found in map
        return EMAP_RID_NOT_FOUND;
    return itr->second._lock ? EMAP_TRUE : EMAP_FALSE;
}

void
enq_map::rid_list(std::vector<uint64_t>& rv)
{
    rv.clear();
    {
        slock s(_mutex);
        for (emap_itr itr = _map.begin(); itr != _map.end(); itr++)
            rv.push_back(itr->first);
    }
}

void
enq_map::pfid_list(std::vector<uint16_t>& fv)
{
    fv.clear();
    {
        slock s(_mutex);
        for (emap_itr itr = _map.begin(); itr != _map.end(); itr++)
            fv.push_back(itr->second._pfid);
    }
}

} // namespace journal
} // namespace mrg
