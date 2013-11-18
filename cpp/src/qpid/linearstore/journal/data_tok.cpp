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

#include "qpid/linearstore/journal/data_tok.h"

#include <iomanip>
#include "qpid/linearstore/journal/slock.h"

namespace qpid {
namespace linearstore {
namespace journal {

// Static members

uint64_t data_tok::_cnt = 0;
smutex data_tok::_mutex;

data_tok::data_tok():
    _wstate(NONE),
    _dsize(0),
    _dblks_written(0),
    _pg_cnt(0),
    _fid(0),
    _rid(0),
    _xid(),
    _dequeue_rid(0),
    _external_rid(false)
{
    slock s(_mutex);
    _icnt = _cnt++;
}

data_tok::~data_tok() {}

const char*
data_tok::wstate_str() const
{
    return wstate_str(_wstate);
}

const char*
data_tok::wstate_str(write_state wstate)
{
    switch (wstate)
    {
        case NONE:
            return "NONE";
        case ENQ_CACHED:
            return "ENQ_CACHED";
        case ENQ_PART:
            return "ENQ_PART";
        case ENQ_SUBM:
            return "ENQ_SUBM";
        case ENQ:
            return "ENQ";
        case DEQ_CACHED:
            return "DEQ_CACHED";
        case DEQ_PART:
            return "DEQ_PART";
        case DEQ_SUBM:
            return "DEQ_SUBM";
        case DEQ:
            return "DEQ";
        case ABORT_CACHED:
            return "ABORT_CACHED";
        case ABORT_PART:
            return "ABORT_PART";
        case ABORT_SUBM:
            return "ABORT_SUBM";
        case ABORTED:
            return "ABORTED";
        case COMMIT_CACHED:
            return "COMMIT_CACHED";
        case COMMIT_PART:
            return "COMMIT_PART";
        case COMMIT_SUBM:
            return "COMMIT_SUBM";
        case COMMITTED:
            return "COMMITTED";
    }
    // Not using default: forces compiler to ensure all cases are covered.
    return "<wstate unknown>";
}

void
data_tok::reset()
{
    _wstate = NONE;
    _dsize = 0;
    _dblks_written = 0;
    _pg_cnt = 0;
    _fid = 0;
    _rid = 0;
    _xid.clear();
}

// debug aid
std::string
data_tok::status_str() const
{
    std::ostringstream oss;
    oss << std::hex << std::setfill('0');
    oss << "dtok id=0x" << _icnt << "; ws=" << wstate_str()/* << "; rs=" << rstate_str()*/;
    oss << "; fid=0x" << _fid << "; rid=0x" << _rid << "; xid=";
    for (unsigned i=0; i<_xid.size(); i++)
    {
        if (isprint(_xid[i]))
            oss << _xid[i];
        else
            oss << "/" << std::setw(2) << (int)((char)_xid[i]);
    }
    oss << "; drid=0x" << _dequeue_rid << " extrid=" << (_external_rid?"T":"F");
    oss << "; ds=0x" << _dsize << "; dw=0x" << _dblks_written/* << "; dr=0x" << _dblks_read*/;
    oss << "; pc=0x" << _pg_cnt;
    return oss.str();
}

}}}
