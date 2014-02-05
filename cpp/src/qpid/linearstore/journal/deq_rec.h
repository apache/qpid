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

#ifndef QPID_LINEARSTORE_JOURNAL_DEQ_REQ_H
#define QPID_LINEARSTORE_JOURNAL_DEQ_REQ_H

#include "qpid/linearstore/journal/jrec.h"
#include "qpid/linearstore/journal/utils/deq_hdr.h"
#include "qpid/linearstore/journal/utils/rec_tail.h"

namespace qpid {
namespace linearstore {
namespace journal {

/**
* \class deq_rec
* \brief Class to handle a single journal dequeue record.
*/
class deq_rec : public jrec
{
private:
    ::deq_hdr_t _deq_hdr;   ///< Local instance of dequeue header struct
    const void* _xidp;      ///< xid pointer for encoding (writing to disk)
    void* _xid_buff;        ///< Pointer to buffer to receive xid read from disk
    ::rec_tail_t _deq_tail; ///< Local instance of enqueue tail struct, only encoded if XID is present

public:
    deq_rec();
    virtual ~deq_rec();

    void reset(const uint64_t serial, const uint64_t rid, const  uint64_t drid, const void* const xidp,
               const std::size_t xidlen, const bool txn_coml_commit);
    uint32_t encode(void* wptr, uint32_t rec_offs_dblks, uint32_t max_size_dblks, Checksum& checksum);
    bool decode(::rec_hdr_t& h, std::ifstream* ifsp, std::size_t& rec_offs, const std::streampos rec_start);

    inline bool is_txn_coml_commit() const { return ::is_txn_coml_commit(&_deq_hdr); }
    inline uint64_t rid() const { return _deq_hdr._rhdr._rid; }
    inline uint64_t deq_rid() const { return _deq_hdr._deq_rid; }
    std::size_t get_xid(void** const xidpp);
    std::string& str(std::string& str) const;
    inline std::size_t data_size() const { return 0; } // This record never carries data
    std::size_t xid_size() const;
    std::size_t rec_size() const;
    void check_rec_tail(const std::streampos rec_start) const;

private:
    virtual void clean();
};

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_DEQ_REQ_H
