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
 * \file rcvdat.h
 *
 * Qpid asynchronous store plugin library
 *
 * Contains structure for recovery status and offset data.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_RCVDAT_H
#define QPID_LEGACYSTORE_JRNL_RCVDAT_H

#include <cstddef>
#include <iomanip>
#include <map>
#include "qpid/legacystore/jrnl/jcfg.h"
#include <sstream>
#include <sys/types.h>
#include <vector>

namespace mrg
{
namespace journal
{

        struct rcvdat
        {
            u_int16_t _njf;     ///< Number of journal files
            bool _ae;           ///< Auto-expand mode
            u_int16_t _aemjf;   ///< Auto-expand mode max journal files
            bool _owi;          ///< Overwrite indicator
            bool _frot;         ///< First rotation flag
            bool _jempty;       ///< Journal data files empty
            u_int16_t _ffid;    ///< First file id
            std::size_t _fro;   ///< First record offset in ffid
            u_int16_t _lfid;    ///< Last file id
            std::size_t _eo;    ///< End offset (first byte past last record)
            u_int64_t _h_rid;   ///< Highest rid found
            bool _lffull;       ///< Last file is full
            bool _jfull;        ///< Journal is full
            std::vector<u_int16_t> _fid_list; ///< Fid-lid mapping - list of fids in order of lid
            std::vector<u_int32_t> _enq_cnt_list; ///< Number enqueued records found for each file

            rcvdat():
                    _njf(0),
                    _ae(false),
                    _aemjf(0),
                    _owi(false),
                    _frot(false),
                    _jempty(true),
                    _ffid(0),
                    _fro(0),
                    _lfid(0),
                    _eo(0),
                    _h_rid(0),
                    _lffull(false),
                    _jfull(false),
                    _fid_list(),
                    _enq_cnt_list()
            {}

            void reset(const u_int16_t num_jfiles, const bool auto_expand, const u_int16_t ae_max_jfiles)
            {
                _njf = num_jfiles;
                _ae = auto_expand;
                _aemjf = ae_max_jfiles;
                _owi = false;
                _frot = false;
                _jempty = true;
                _ffid = 0;
                _fro = 0;
                _lfid = 0;
                _eo = 0;
                _h_rid = 0;
                _lffull = false;
                _jfull = false;
                _fid_list.clear();
                _enq_cnt_list.clear();
                _enq_cnt_list.resize(num_jfiles, 0);
            }

            // Find first fid with enqueued records
            u_int16_t ffid()
            {
                u_int16_t index = _ffid;
                while (index != _lfid && _enq_cnt_list[index] == 0)
                {
                    if (++index >= _njf)
                        index = 0;
                }
                return index;
            }

            std::string to_string(const std::string& jid)
            {
                std::ostringstream oss;
                oss << "Recover file analysis (jid=\"" << jid << "\"):" << std::endl;
                oss << "  Number of journal files (_njf) = " << _njf << std::endl;
                oss << "  Auto-expand mode (_ae) = " << (_ae ? "TRUE" : "FALSE") << std::endl;
                if (_ae) oss << "  Auto-expand mode max journal files (_aemjf) = " << _aemjf << std::endl;
                oss << "  Overwrite indicator (_owi) = " << (_owi ? "TRUE" : "FALSE") << std::endl;
                oss << "  First rotation (_frot) = " << (_frot ? "TRUE" : "FALSE") << std::endl;
                oss << "  Journal empty (_jempty) = " << (_jempty ? "TRUE" : "FALSE") << std::endl;
                oss << "  First (earliest) fid (_ffid) = " << _ffid << std::endl;
                oss << "  First record offset in first fid (_fro) = 0x" << std::hex << _fro <<
                        std::dec << " (" << (_fro/JRNL_DBLK_SIZE) << " dblks)" << std::endl;
                oss << "  Last (most recent) fid (_lfid) = " << _lfid << std::endl;
                oss << "  End offset (_eo) = 0x" << std::hex << _eo << std::dec << " ("  <<
                        (_eo/JRNL_DBLK_SIZE) << " dblks)" << std::endl;
                oss << "  Highest rid (_h_rid) = 0x" << std::hex << _h_rid << std::dec << std::endl;
                oss << "  Last file full (_lffull) = " << (_lffull ? "TRUE" : "FALSE") << std::endl;
                oss << "  Journal full (_jfull) = " << (_jfull ? "TRUE" : "FALSE") << std::endl;
                oss << "  Normalized fid list (_fid_list) = [";
                for (std::vector<u_int16_t>::const_iterator i = _fid_list.begin(); i < _fid_list.end(); i++)
                {
                    if (i != _fid_list.begin()) oss << ", ";
                    oss << *i;
                }
                oss << "]" << std::endl;
                oss << "  Enqueued records (txn & non-txn):" << std::endl;
                for (unsigned i=0; i<_enq_cnt_list.size(); i++)
                   oss << "    File " << std::setw(2) << i << ": " << _enq_cnt_list[i] <<
                            std::endl;
                return oss.str();
            }

            std::string to_log(const std::string& jid)
            {
                std::ostringstream oss;
                oss << "Recover file analysis (jid=\"" << jid << "\"):";
                oss << " njf=" << _njf;
                oss << " ae=" << (_owi ? "T" : "F");
                oss << " aemjf=" << _aemjf;
                oss << " owi=" << (_ae ? "T" : "F");
                oss << " frot=" << (_frot ? "T" : "F");
                oss << " jempty=" << (_jempty ? "T" : "F");
                oss << " ffid=" << _ffid;
                oss << " fro=0x" << std::hex << _fro << std::dec << " (" <<
                        (_fro/JRNL_DBLK_SIZE) << " dblks)";
                oss << " lfid=" << _lfid;
                oss << " eo=0x" << std::hex << _eo << std::dec << " ("  <<
                        (_eo/JRNL_DBLK_SIZE) << " dblks)";
                oss << " h_rid=0x" << std::hex << _h_rid << std::dec;
                oss << " lffull=" << (_lffull ? "T" : "F");
                oss << " jfull=" << (_jfull ? "T" : "F");
                oss << " Enqueued records (txn & non-txn): [ ";
                for (unsigned i=0; i<_enq_cnt_list.size(); i++)
                {
                    if (i) oss << " ";
                    oss << "fid_" << std::setw(2) << std::setfill('0') << i << "=" << _enq_cnt_list[i];
                }
                oss << " ]";
                return oss.str();
            }
        };
} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_RCVDAT_H
