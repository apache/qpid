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

#include "qpid/linearstore/journal/txn_map.h"

#include "qpid/linearstore/journal/slock.h"

namespace qpid {
namespace linearstore {
namespace journal {

// return/error codes
int16_t txn_map::TMAP_RID_NOT_FOUND = -2;
int16_t txn_map::TMAP_XID_NOT_FOUND = -1;
int16_t txn_map::TMAP_OK = 0;
int16_t txn_map::TMAP_NOT_SYNCED = 0;
int16_t txn_map::TMAP_SYNCED = 1;

txn_data_t::txn_data_t(const uint64_t rid,
                       const uint64_t drid,
                       const uint64_t fid,
                       const uint64_t foffs,
                       const bool enq_flag,
                       const bool tpc_flag,
                       const bool commit_flag):
        rid_(rid),
        drid_(drid),
        fid_(fid),
        foffs_(foffs),
        enq_flag_(enq_flag),
        tpc_flag_(tpc_flag),
        commit_flag_(commit_flag),
        aio_compl_(false)
{}

txn_op_stats_t::txn_op_stats_t(const txn_data_list_t& tdl) :
        enqCnt(0U),
        deqCnt(0U),
        tpcCnt(0U),
        abortCnt(0U),
        commitCnt(0U),
        rid(0ULL)
{
    for (tdl_const_itr_t i=tdl.begin(); i!=tdl.end(); ++i) {
        if (i->enq_flag_) {
            ++enqCnt;
            rid = i->rid_;
        } else {
            ++deqCnt;
            if (i->commit_flag_) {
                ++commitCnt;
            } else {
                ++abortCnt;
            }
        }
        if (i->tpc_flag_) {
            ++tpcCnt;
        }
    }
    if (tpcCnt > 0 && tpcCnt != tdl.size()) {
        throw jexception("Inconsistent 2PC count"); // TODO: complete exception details
    }
    if (abortCnt > 0 && commitCnt > 0) {
        throw jexception("Both abort and commit in same transaction"); // TODO: complete exception details
    }
}

txn_map::txn_map():
        _map()/*,
        _pfid_txn_cnt()*/
{}

txn_map::~txn_map() {}

bool
txn_map::insert_txn_data(const std::string& xid, const txn_data_t& td)
{
    bool ok = true;
    slock s(_mutex);
    xmap_itr itr = _map.find(xid);
    if (itr == _map.end()) // not found in map
    {
        txn_data_list_t list;
        list.push_back(td);
        std::pair<xmap_itr, bool> ret = _map.insert(xmap_param(xid, list));
        if (!ret.second) // duplicate
            ok = false;
    }
    else
        itr->second.push_back(td);
    return ok;
}

const txn_data_list_t
txn_map::get_tdata_list(const std::string& xid)
{
    slock s(_mutex);
    return get_tdata_list_nolock(xid);
}

const txn_data_list_t
txn_map::get_tdata_list_nolock(const std::string& xid)
{
    xmap_itr itr = _map.find(xid);
    if (itr == _map.end()) // not found in map
        return _empty_data_list;
    return itr->second;
}

const txn_data_list_t
txn_map::get_remove_tdata_list(const std::string& xid)
{
    slock s(_mutex);
    xmap_itr itr = _map.find(xid);
    if (itr == _map.end()) // not found in map
        return _empty_data_list;
    txn_data_list_t list = itr->second;
    _map.erase(itr);
    return list;
}

bool
txn_map::in_map(const std::string& xid)
{
    slock s(_mutex);
    xmap_itr itr= _map.find(xid);
    return itr != _map.end();
}

uint32_t
txn_map::enq_cnt()
{
    return cnt(true);
}

uint32_t
txn_map::deq_cnt()
{
    return cnt(true);
}

uint32_t
txn_map::cnt(const bool enq_flag)
{
    slock s(_mutex);
    uint32_t c = 0;
    for (xmap_itr i = _map.begin(); i != _map.end(); i++)
    {
        for (tdl_itr_t j = i->second.begin(); j < i->second.end(); j++)
        {
            if (j->enq_flag_ == enq_flag)
                c++;
        }
    }
    return c;
}

int16_t
txn_map::is_txn_synced(const std::string& xid)
{
    slock s(_mutex);
    xmap_itr itr = _map.find(xid);
    if (itr == _map.end()) // not found in map
        return TMAP_XID_NOT_FOUND;
    bool is_synced = true;
    for (tdl_itr_t litr = itr->second.begin(); litr < itr->second.end(); litr++)
    {
        if (!litr->aio_compl_)
        {
            is_synced = false;
            break;
        }
    }
    return is_synced ? TMAP_SYNCED : TMAP_NOT_SYNCED;
}

int16_t
txn_map::set_aio_compl(const std::string& xid, const uint64_t rid)
{
    slock s(_mutex);
    xmap_itr itr = _map.find(xid);
    if (itr == _map.end()) // xid not found in map
        return TMAP_XID_NOT_FOUND;
    for (tdl_itr_t litr = itr->second.begin(); litr < itr->second.end(); litr++)
    {
        if (litr->rid_ == rid)
        {
            litr->aio_compl_ = true;
            return TMAP_OK; // rid found
        }
    }
    // xid present, but rid not found
    return TMAP_RID_NOT_FOUND;
}

bool
txn_map::data_exists(const std::string& xid, const uint64_t rid)
{
    bool found = false;
    {
        slock s(_mutex);
        txn_data_list_t tdl = get_tdata_list_nolock(xid);
        tdl_itr_t itr = tdl.begin();
        while (itr != tdl.end() && !found)
        {
            found = itr->rid_ == rid;
            itr++;
        }
    }
    return found;
}

bool
txn_map::is_enq(const uint64_t rid)
{
    bool found = false;
    {
        slock s(_mutex);
        for (xmap_itr i = _map.begin(); i != _map.end() && !found; i++)
        {
            txn_data_list_t list = i->second;
            for (tdl_itr_t j = list.begin(); j < list.end() && !found; j++)
            {
                if (j->enq_flag_)
                    found = j->rid_ == rid;
                else
                    found = j->drid_ == rid;
            }
        }
    }
    return found;
}

void
txn_map::xid_list(std::vector<std::string>& xv)
{
    xv.clear();
    {
        slock s(_mutex);
        for (xmap_itr itr = _map.begin(); itr != _map.end(); itr++)
            xv.push_back(itr->first);
    }
}

}}}
