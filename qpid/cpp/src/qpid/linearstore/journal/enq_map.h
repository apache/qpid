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

#ifndef QPID_LINEARSTORE_JOURNAL_ENQ_MAP_H
#define QPID_LINEARSTORE_JOURNAL_ENQ_MAP_H

#include "qpid/linearstore/journal/smutex.h"
#include <vector>

namespace qpid {
namespace linearstore {
namespace journal {

/**
* \class enq_map
* \brief Class for storing the physical file id (pfid) and a transaction locked flag for each enqueued
*     data block using the record id (rid) as a key. This is the primary mechanism for
*     deterimining the enqueue low water mark: if a pfid exists in this map, then there is
*     at least one still-enqueued record in that file. (The transaction map must also be
*     clear, however.)
*
* Map rids against pfid and lock status. As records are enqueued, they are added to this
* map, and as they are dequeued, they are removed. An enqueue is locked when a transactional
* dequeue is pending that has been neither committed nor aborted.
* <pre>
*   key      data
*
*   rid1 --- [ pfid, txn_lock ]
*   rid2 --- [ pfid, txn_lock ]
*   rid3 --- [ pfid, txn_lock ]
*   ...
* </pre>
*/
class enq_map
{
public:
    // return/error codes
    static short EMAP_DUP_RID;
    static short EMAP_LOCKED;
    static short EMAP_RID_NOT_FOUND;
    static short EMAP_OK;
    static short EMAP_FALSE;
    static short EMAP_TRUE;

    typedef struct emap_data_struct_t {
        uint64_t        _pfid;
        std::streampos  _file_posn;
        bool            _lock;
        emap_data_struct_t() : _pfid(0), _file_posn(0), _lock(false) {}
        emap_data_struct_t(const uint64_t pfid, const std::streampos file_posn, const bool lock) : _pfid(pfid), _file_posn(file_posn), _lock(lock) {}
    } emqp_data_struct_t;
    typedef std::pair<uint64_t, emap_data_struct_t> emap_param;
    typedef std::map<uint64_t, emap_data_struct_t> emap;
    typedef emap::iterator emap_itr;

private:
    emap _map;
    smutex _mutex;

public:
    enq_map();
    virtual ~enq_map();

    short insert_pfid(const uint64_t rid, const uint64_t pfid, const std::streampos file_posn); // 0=ok; -3=duplicate rid;
    short insert_pfid(const uint64_t rid, const uint64_t pfid, const std::streampos file_posn, const bool locked); // 0=ok; -3=duplicate rid;
    short get_pfid(const uint64_t rid, uint64_t& pfid); // >=0=pfid; -1=rid not found; -2=locked
    short get_remove_pfid(const uint64_t rid, uint64_t& pfid, const bool txn_flag = false); // >=0=pfid; -1=rid not found; -2=locked
    short get_file_posn(const uint64_t rid, std::streampos& file_posn); // -1=rid not found; -2=locked
    short get_data(const uint64_t rid, emap_data_struct_t& eds);
    bool is_enqueued(const uint64_t rid, bool ignore_lock = false);
    short lock(const uint64_t rid); // 0=ok; -1=rid not found
    short unlock(const uint64_t rid); // 0=ok; -1=rid not found
    short is_locked(const uint64_t rid); // 1=true; 0=false; -1=rid not found
    inline void clear() { _map.clear(); }
    inline bool empty() const { return _map.empty(); }
    inline uint32_t size() const { return uint32_t(_map.size()); }
    void rid_list(std::vector<uint64_t>& rv);
    void pfid_list(std::vector<uint64_t>& fv);
};

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_ENQ_MAP_H
