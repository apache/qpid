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

#ifndef QPID_LEGACYSTORE_JRNL_RCVDAT_H
#define QPID_LEGACYSTORE_JRNL_RCVDAT_H

#include <cstddef>
#include <iomanip>
#include <map>
#include "qpid/linearstore/jrnl/jcfg.h"
#include <sstream>
#include <stdint.h>
#include <vector>

namespace qpid
{
namespace qls_jrnl
{

        struct rcvdat
        {
            std::vector<std::string> _jfl;          ///< Journal file list
            std::map<uint64_t, std::string> _fm;    ///< File number - name map
            std::vector<uint32_t> _enq_cnt_list;    ///< Number enqueued records found for each file
            bool _jempty;                           ///< Journal data files empty
            std::size_t _fro;                       ///< First record offset in ffid
            std::size_t _eo;                        ///< End offset (first byte past last record)
            uint64_t _h_rid;                        ///< Highest rid found
            bool _lffull;                           ///< Last file is full

            rcvdat():
                    _jfl(),
                    _fm(),
                    _enq_cnt_list(),
                    _jempty(false),
                    _fro(0),
                    _eo(0),
                    _h_rid(0),
                    _lffull(false)
            {}

            std::string to_string(const std::string& jid)
            {
                std::ostringstream oss;
                oss << "Recover file analysis (jid=\"" << jid << "\"):" << std::endl;
                oss << "  Number of journal files = " << _fm.size() << std::endl;
                oss << "  Journal File List (_jfl):";
                for (std::map<uint64_t, std::string>::const_iterator i=_fm.begin(); i!=_fm.end(); ++i) {
                    oss << "    " << i->first << ": " << i->second.substr(i->second.rfind('/')+1) << std::endl;
                }
                oss << "  Journal empty (_jempty) = " << (_jempty ? "TRUE" : "FALSE") << std::endl;
                oss << "  First record offset in first fid (_fro) = 0x" << std::hex << _fro <<
                        std::dec << " (" << (_fro/JRNL_DBLK_SIZE_BYTES) << " dblks)" << std::endl;
                oss << "  End offset (_eo) = 0x" << std::hex << _eo << std::dec << " ("  <<
                        (_eo/JRNL_DBLK_SIZE_BYTES) << " dblks)" << std::endl;
                oss << "  Highest rid (_h_rid) = 0x" << std::hex << _h_rid << std::dec << std::endl;
                oss << "  Last file full (_lffull) = " << (_lffull ? "TRUE" : "FALSE") << std::endl;
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
                oss << " jfl=[";
                for (std::map<uint64_t, std::string>::const_iterator i=_fm.begin(); i!=_fm.end(); ++i) {
                    if (i!=_fm.begin()) oss << " ";
                    oss << i->first << ":" << i->second.substr(i->second.rfind('/')+1);
                }
                oss << "]";
                oss << " _enq_cnt_list: [ ";
                for (unsigned i=0; i<_enq_cnt_list.size(); i++) {
                    if (i) oss << " ";
                    oss << _enq_cnt_list[i];
                }
                oss << " ]";
                oss << " jempty=" << (_jempty ? "T" : "F");
                oss << " fro=0x" << std::hex << _fro << std::dec << " (" <<
                        (_fro/JRNL_DBLK_SIZE_BYTES) << " dblks)";
                oss << " eo=0x" << std::hex << _eo << std::dec << " ("  <<
                        (_eo/JRNL_DBLK_SIZE_BYTES) << " dblks)";
                oss << " h_rid=0x" << std::hex << _h_rid << std::dec;
                oss << " lffull=" << (_lffull ? "T" : "F");
                return oss.str();
            }
        };
}}

#endif // ifndef QPID_LEGACYSTORE_JRNL_RCVDAT_H
