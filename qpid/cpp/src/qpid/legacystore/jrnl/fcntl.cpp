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
 * \file fcntl.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::fcntl (non-logging file
 * handle), used for controlling journal log files. See comments in file
 * fcntl.h for details.
 */

#include "qpid/legacystore/jrnl/fcntl.h"

#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <iomanip>
#include "qpid/legacystore/jrnl/jerrno.h"
#include "qpid/legacystore/jrnl/jexception.h"
#include <sstream>
#include <unistd.h>

namespace mrg
{
namespace journal
{

fcntl::fcntl(const std::string& fbasename, const u_int16_t pfid, const u_int16_t lfid, const u_int32_t jfsize_sblks,
        const rcvdat* const ro):
        _fname(),
        _pfid(pfid),
        _lfid(lfid),
        _ffull_dblks(JRNL_SBLK_SIZE * (jfsize_sblks + 1)),
        _wr_fh(-1),
        _rec_enqcnt(0),
        _rd_subm_cnt_dblks(0),
        _rd_cmpl_cnt_dblks(0),
        _wr_subm_cnt_dblks(0),
        _wr_cmpl_cnt_dblks(0),
        _aio_cnt(0),
        _fhdr_wr_aio_outstanding(false)
{
    initialize(fbasename, pfid, lfid, jfsize_sblks, ro);
    open_wr_fh();
}

fcntl::~fcntl()
{
    close_wr_fh();
}

bool
fcntl::reset(const rcvdat* const ro)
{
    rd_reset();
    return wr_reset(ro);
}

void
fcntl::rd_reset()
{
    _rd_subm_cnt_dblks = 0;
    _rd_cmpl_cnt_dblks = 0;
}

bool
fcntl::wr_reset(const rcvdat* const ro)
{
    if (ro)
    {
        if (!ro->_jempty)
        {
            if (ro->_lfid == _pfid)
            {
                _wr_subm_cnt_dblks = ro->_eo/JRNL_DBLK_SIZE;
                _wr_cmpl_cnt_dblks = ro->_eo/JRNL_DBLK_SIZE;
            }
            else
            {
                _wr_subm_cnt_dblks = _ffull_dblks;
                _wr_cmpl_cnt_dblks = _ffull_dblks;
            }
            _rec_enqcnt = ro->_enq_cnt_list[_pfid];
            return true;
        }
    }
    // Journal overflow test - checks if the file to be reset still contains enqueued records
    // or outstanding aios
    if (_rec_enqcnt || _aio_cnt)
        return false;
    _wr_subm_cnt_dblks = 0;
    _wr_cmpl_cnt_dblks = 0;
    return true;
}

int
fcntl::open_wr_fh()
{
    if (_wr_fh < 0)
    {
        _wr_fh = ::open(_fname.c_str(), O_WRONLY | O_DIRECT, S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH); // 0644 -rw-r--r--
        if (_wr_fh < 0)
        {
            std::ostringstream oss;
            oss << "pfid=" << _pfid << " lfid=" << _lfid << " file=\"" << _fname << "\"" << FORMAT_SYSERR(errno);
            throw jexception(jerrno::JERR_FCNTL_OPENWR, oss.str(), "fcntl", "open_fh");
        }
    }
    return _wr_fh;
}

void
fcntl::close_wr_fh()
{
    if (_wr_fh >= 0)
    {
        ::close(_wr_fh);
        _wr_fh = -1;
    }
}

u_int32_t
fcntl::add_enqcnt(u_int32_t a)
{
    _rec_enqcnt += a;
    return _rec_enqcnt;
}

u_int32_t
fcntl::decr_enqcnt()
{
    if (_rec_enqcnt == 0)
    {
        std::ostringstream oss;
        oss << "pfid=" << _pfid << " lfid=" << _lfid;
        throw jexception(jerrno::JERR__UNDERFLOW, oss.str(), "fcntl", "decr_enqcnt");
    }
    return --_rec_enqcnt;
}

u_int32_t
fcntl::subtr_enqcnt(u_int32_t s)
{
    if (_rec_enqcnt < s)
    {
        std::ostringstream oss;
        oss << "pfid=" << _pfid << " lfid=" << _lfid << " rec_enqcnt=" << _rec_enqcnt << " decr=" << s;
        throw jexception(jerrno::JERR__UNDERFLOW, oss.str(), "fcntl", "subtr_enqcnt");
    }
    _rec_enqcnt -= s;
    return _rec_enqcnt;
}

u_int32_t
fcntl::add_rd_subm_cnt_dblks(u_int32_t a)
{
    if (_rd_subm_cnt_dblks + a > _wr_subm_cnt_dblks)
    {
        std::ostringstream oss;
        oss << "pfid=" << _pfid << " lfid=" << _lfid << " rd_subm_cnt_dblks=" << _rd_subm_cnt_dblks << " incr=" << a;
        oss << " wr_subm_cnt_dblks=" << _wr_subm_cnt_dblks;
        throw jexception(jerrno::JERR_FCNTL_RDOFFSOVFL, oss.str(), "fcntl", "add_rd_subm_cnt_dblks");
    }
    _rd_subm_cnt_dblks += a;
    return _rd_subm_cnt_dblks;
}

u_int32_t
fcntl::add_rd_cmpl_cnt_dblks(u_int32_t a)
{
    if (_rd_cmpl_cnt_dblks + a > _rd_subm_cnt_dblks)
    {
        std::ostringstream oss;
        oss << "pfid=" << _pfid << " lfid=" << _lfid << " rd_cmpl_cnt_dblks=" << _rd_cmpl_cnt_dblks << " incr=" << a;
        oss << " rd_subm_cnt_dblks=" << _rd_subm_cnt_dblks;
        throw jexception(jerrno::JERR_FCNTL_CMPLOFFSOVFL, oss.str(), "fcntl", "add_rd_cmpl_cnt_dblks");
    }
    _rd_cmpl_cnt_dblks += a;
    return _rd_cmpl_cnt_dblks;
}

u_int32_t
fcntl::add_wr_subm_cnt_dblks(u_int32_t a)
{
    if (_wr_subm_cnt_dblks + a > _ffull_dblks) // Allow for file header
    {
        std::ostringstream oss;
        oss << "pfid=" << _pfid << " lfid=" << _lfid << " wr_subm_cnt_dblks=" << _wr_subm_cnt_dblks << " incr=" << a;
        oss << " fsize=" << _ffull_dblks << " dblks";
        throw jexception(jerrno::JERR_FCNTL_FILEOFFSOVFL, oss.str(), "fcntl", "add_wr_subm_cnt_dblks");
    }
    _wr_subm_cnt_dblks += a;
    return _wr_subm_cnt_dblks;
}

u_int32_t
fcntl::add_wr_cmpl_cnt_dblks(u_int32_t a)
{
    if (_wr_cmpl_cnt_dblks + a > _wr_subm_cnt_dblks)
    {
        std::ostringstream oss;
        oss << "pfid=" << _pfid << " lfid=" << _lfid << " wr_cmpl_cnt_dblks=" << _wr_cmpl_cnt_dblks << " incr=" << a;
        oss << " wr_subm_cnt_dblks=" << _wr_subm_cnt_dblks;
        throw jexception(jerrno::JERR_FCNTL_CMPLOFFSOVFL, oss.str(), "fcntl", "add_wr_cmpl_cnt_dblks");
    }
    _wr_cmpl_cnt_dblks += a;
    return _wr_cmpl_cnt_dblks;
}

u_int16_t
fcntl::decr_aio_cnt()
{
    if(_aio_cnt == 0)
    {
        std::ostringstream oss;
        oss << "pfid=" << _pfid << " lfid=" << _lfid << " Decremented aio_cnt to below zero";
        throw jexception(jerrno::JERR__UNDERFLOW, oss.str(), "fcntl", "decr_aio_cnt");
    }
    return --_aio_cnt;
}

// Debug function
const std::string
fcntl::status_str() const
{
    std::ostringstream oss;
    oss << "pfid=" << _pfid << " ws=" << _wr_subm_cnt_dblks << " wc=" << _wr_cmpl_cnt_dblks;
    oss << " rs=" << _rd_subm_cnt_dblks << " rc=" << _rd_cmpl_cnt_dblks;
    oss << " ec=" << _rec_enqcnt << " ac=" << _aio_cnt;
    return oss.str();
}

// Protected functions

void
fcntl::initialize(const std::string& fbasename, const u_int16_t pfid, const u_int16_t lfid, const u_int32_t jfsize_sblks,
        const rcvdat* const ro)
{
    _pfid = pfid;
    _lfid = lfid;
    _fname = filename(fbasename, pfid);

#ifdef RHM_JOWRITE
    // In test mode, only create file if it does not exist
    struct stat s;
    if (::stat(_fname.c_str(), &s))
    {
#endif
        if (ro) // Recovery initialization: set counters only
        {
            if (!ro->_jempty)
            {
                // For last file only, set write counters to end of last record (the
                // continuation point); for all others, set to eof.
                if (ro->_lfid == _pfid)
                {
                    _wr_subm_cnt_dblks = ro->_eo/JRNL_DBLK_SIZE;
                    _wr_cmpl_cnt_dblks = ro->_eo/JRNL_DBLK_SIZE;
                }
                else
                {
                    _wr_subm_cnt_dblks = _ffull_dblks;
                    _wr_cmpl_cnt_dblks = _ffull_dblks;
                }
                // Set the number of enqueued records for this file.
                _rec_enqcnt = ro->_enq_cnt_list[_pfid];
            }
        }
        else // Normal initialization: create empty journal files
            create_jfile(jfsize_sblks);
#ifdef RHM_JOWRITE
    }
#endif
}

std::string
fcntl::filename(const std::string& fbasename, const u_int16_t pfid)
{
    std::ostringstream oss;
    oss << fbasename << ".";
    oss << std::setw(4) << std::setfill('0') << std::hex << pfid;
    oss << "." << JRNL_DATA_EXTENSION;
    return oss.str();
}

void
fcntl::clean_file(const u_int32_t jfsize_sblks)
{
    // NOTE: The journal file size is always one sblock bigger than the specified journal
    // file size, which is the data content size. The extra block is for the journal file
    // header which precedes all data on each file and is exactly one sblock in size.
    u_int32_t nsblks = jfsize_sblks + 1;

    // TODO - look at more efficient alternatives to allocating a null block:
    // 1. mmap() against /dev/zero, but can alignment for O_DIRECT be assured?
    // 2. ftruncate(), but does this result in a sparse file? If so, then this is no good.

    // Create temp null block for writing
    const std::size_t sblksize = JRNL_DBLK_SIZE * JRNL_SBLK_SIZE;
    void* nullbuf = 0;
    // Allocate no more than 2MB (4096 sblks) as a null buffer
    const u_int32_t nullbuffsize_sblks = nsblks > 4096 ? 4096 : nsblks;
    const std::size_t nullbuffsize = nullbuffsize_sblks * sblksize;
    if (::posix_memalign(&nullbuf, sblksize, nullbuffsize))
    {
        std::ostringstream oss;
        oss << "posix_memalign() failed: size=" << nullbuffsize << " blk_size=" << sblksize;
        oss << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR__MALLOC, oss.str(), "fcntl", "clean_file");
    }
    std::memset(nullbuf, 0, nullbuffsize);

    int fh = ::open(_fname.c_str(), O_WRONLY | O_CREAT | O_DIRECT,
            S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH); // 0644 -rw-r--r--
    if (fh < 0)
    {
        std::free(nullbuf);
        std::ostringstream oss;
        oss << "open() failed:" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_FCNTL_OPENWR, oss.str(), "fcntl", "clean_file");
    }

    while (nsblks > 0)
    {
        u_int32_t this_write_sblks = nsblks >= nullbuffsize_sblks ? nullbuffsize_sblks : nsblks;
        if (::write(fh, nullbuf, this_write_sblks * sblksize) == -1)
        {
            ::close(fh);
            std::free(nullbuf);
            std::ostringstream oss;
            oss << "wr_size=" << (this_write_sblks * sblksize) << FORMAT_SYSERR(errno);
            throw jexception(jerrno::JERR_FCNTL_WRITE, oss.str(), "fcntl", "clean_file");
        }
        nsblks -= this_write_sblks;
    }

    // Clean up
    std::free(nullbuf);
    if (::close(fh))
    {
        std::ostringstream oss;
        oss << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_FCNTL_CLOSE, oss.str(), "fcntl", "clean_file");
    }
}

void
fcntl::create_jfile(const u_int32_t jfsize_sblks)
{
    clean_file(jfsize_sblks);
}

} // namespace journal
} // namespace mrg
