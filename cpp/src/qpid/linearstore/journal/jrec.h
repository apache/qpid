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

#ifndef QPID_LINEARSTORE_JOURNAL_JREC_H
#define QPID_LINEARSTORE_JOURNAL_JREC_H

#include <fstream>
#include "qpid/linearstore/journal/jcfg.h"
#include <stdint.h>

struct rec_hdr_t;

namespace qpid {
namespace linearstore {
namespace journal {

class Checksum;

/**
* \class jrec
* \brief Abstract class for all file jrecords, both data and log. This class establishes
*     the common data format and structure for these jrecords.
*/
class jrec
{
public:
    jrec() {}
    virtual ~jrec() {}

    /**
    * \brief Encode this instance of jrec into the write buffer at the disk-block-aligned
    *   pointer wptr starting at position rec_offs_dblks in the encoded record to a
    *   maximum size of max_size_dblks.
    *
    * This call encodes the content of the data contianed in this instance of jrec into a
    * disk-softblock-aligned (defined by JRNL_SBLK_SIZE) buffer pointed to by parameter
    * wptr. No more than paramter max_size_dblks data-blocks may be written to the buffer.
    * The parameter rec_offs_dblks is the offset in data-blocks within the fully encoded
    * data block this instance represents at which to start encoding.
    *
    * Encoding entails writing the record header (struct enq_hdr), the data and the record tail
    * (struct enq_tail). The record must be data-block-aligned (defined by JRNL_DBLK_SIZE),
    * thus any remaining space in the final data-block is ignored; the returned value is the
    * number of data-blocks consumed from the page by the encode action. Provided the initial
    * alignment requirements are met, records may be of arbitrary size and may span multiple
    * data-blocks, disk-blocks and/or pages.
    *
    * Since the record size in data-blocks is known, the general usage pattern is to call
    * encode() as many times as is needed to fully encode the data. Each call to encode()
    * will encode as much of the record as it can to what remains of the current page cache,
    * and will return the number of data-blocks actually encoded.
    *
    * <b>Example:</b> Assume that record r1 was previously written to page 0, and that this
    * is an instance representing record r2. Being larger than the page size ps, r2 would span
    * multiple pages as follows:
    * <pre>
    *       |<---ps--->|
    *       +----------+----------+----------+----...
    *       |      |r2a|   r2b    |  r2c   | |
    *       |<-r1-><----------r2---------->  |
    *       +----------+----------+----------+----...
    * page:      p0         p1         p2
    * </pre>
    * Encoding record r2 will require multiple calls to encode; one for each page which
    * is involved. Record r2 is divided logically into sections r2a, r2b and r2c at the
    * points where the page boundaries intersect with the record. Assuming a page size
    * of ps, the page boundary pointers are represented by their names p0, p1... and the
    * sizes of the record segments are represented by their names r1, r2a, r2b..., the calls
    * should be as follows:
    * <pre>
    * encode(p0+r1, 0, ps-r1); (returns r2a data-blocks)
    * encode(p1, r2a, ps);     (returns r2b data-blocks which equals ps)
    * encode(p2, r2a+r2b, ps); (returns r2c data-blocks)
    * </pre>
    *
    * \param wptr Data-block-aligned pointer to position in page buffer where encoding is to
    *   take place.
    * \param rec_offs_dblks Offset in data-blocks within record from which to start encoding.
    * \param max_size_dblks Maximum number of data-blocks to write to pointer wptr.
    * \returns Number of data-blocks encoded.
    */
    virtual uint32_t encode(void* wptr, uint32_t rec_offs_dblks, uint32_t max_size_dblks, Checksum& checksum) = 0;
    virtual bool decode(::rec_hdr_t& h, std::ifstream* ifsp, std::size_t& rec_offs, const std::streampos rec_start) = 0;

    virtual std::string& str(std::string& str) const = 0;
    virtual std::size_t data_size() const = 0;
    virtual std::size_t xid_size() const = 0;
    virtual std::size_t rec_size() const = 0;
    inline virtual uint32_t rec_size_dblks() const { return size_dblks(rec_size()); }
    static inline uint32_t size_dblks(const std::size_t size)
            { return size_blks(size, QLS_DBLK_SIZE_BYTES); }
    static inline uint32_t size_sblks(const std::size_t size)
            { return size_blks(size, QLS_SBLK_SIZE_BYTES); }
    static inline uint32_t size_blks(const std::size_t size, const std::size_t blksize)
            { return (size + blksize - 1)/blksize; }
    virtual uint64_t rid() const = 0;

protected:
    virtual void clean() = 0;
};

}}}

#endif // ifndef QPID_LINEARSTORE_JRNL_JREC_H
