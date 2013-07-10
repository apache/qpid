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
 * \file jerrno.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::jerrno (journal error
 * codes). See comments in file jerrno.h for details.
 *
 * See file jerrno.h for class details.
 *
 * \author Kim van der Riet
 */

#include "qpid/legacystore/jrnl/jerrno.h"

namespace mrg
{
namespace journal
{

std::map<u_int32_t, const char*> jerrno::_err_map;
std::map<u_int32_t, const char*>::iterator jerrno::_err_map_itr;
bool jerrno::_initialized = jerrno::__init();

// generic errors
const u_int32_t jerrno::JERR__MALLOC            = 0x0100;
const u_int32_t jerrno::JERR__UNDERFLOW         = 0x0101;
const u_int32_t jerrno::JERR__NINIT             = 0x0102;
const u_int32_t jerrno::JERR__AIO               = 0x0103;
const u_int32_t jerrno::JERR__FILEIO            = 0x0104;
const u_int32_t jerrno::JERR__RTCLOCK           = 0x0105;
const u_int32_t jerrno::JERR__PTHREAD           = 0x0106;
const u_int32_t jerrno::JERR__TIMEOUT           = 0x0107;
const u_int32_t jerrno::JERR__UNEXPRESPONSE     = 0x0108;
const u_int32_t jerrno::JERR__RECNFOUND         = 0x0109;
const u_int32_t jerrno::JERR__NOTIMPL           = 0x010a;

// class jcntl
const u_int32_t jerrno::JERR_JCNTL_STOPPED      = 0x0200;
const u_int32_t jerrno::JERR_JCNTL_READONLY     = 0x0201;
const u_int32_t jerrno::JERR_JCNTL_AIOCMPLWAIT  = 0x0202;
const u_int32_t jerrno::JERR_JCNTL_UNKNOWNMAGIC = 0x0203;
const u_int32_t jerrno::JERR_JCNTL_NOTRECOVERED = 0x0204;
const u_int32_t jerrno::JERR_JCNTL_RECOVERJFULL = 0x0205;
const u_int32_t jerrno::JERR_JCNTL_OWIMISMATCH  = 0x0206;

// class jdir
const u_int32_t jerrno::JERR_JDIR_NOTDIR        = 0x0300;
const u_int32_t jerrno::JERR_JDIR_MKDIR         = 0x0301;
const u_int32_t jerrno::JERR_JDIR_OPENDIR       = 0x0302;
const u_int32_t jerrno::JERR_JDIR_READDIR       = 0x0303;
const u_int32_t jerrno::JERR_JDIR_CLOSEDIR      = 0x0304;
const u_int32_t jerrno::JERR_JDIR_RMDIR         = 0x0305;
const u_int32_t jerrno::JERR_JDIR_NOSUCHFILE    = 0x0306;
const u_int32_t jerrno::JERR_JDIR_FMOVE         = 0x0307;
const u_int32_t jerrno::JERR_JDIR_STAT          = 0x0308;
const u_int32_t jerrno::JERR_JDIR_UNLINK        = 0x0309;
const u_int32_t jerrno::JERR_JDIR_BADFTYPE      = 0x030a;

// class fcntl
const u_int32_t jerrno::JERR_FCNTL_OPENWR       = 0x0400;
const u_int32_t jerrno::JERR_FCNTL_WRITE        = 0x0401;
const u_int32_t jerrno::JERR_FCNTL_CLOSE        = 0x0402;
const u_int32_t jerrno::JERR_FCNTL_FILEOFFSOVFL = 0x0403;
const u_int32_t jerrno::JERR_FCNTL_CMPLOFFSOVFL = 0x0404;
const u_int32_t jerrno::JERR_FCNTL_RDOFFSOVFL   = 0x0405;

// class lfmgr
const u_int32_t jerrno::JERR_LFMGR_BADAEFNUMLIM = 0x0500;
const u_int32_t jerrno::JERR_LFMGR_AEFNUMLIMIT  = 0x0501;
const u_int32_t jerrno::JERR_LFMGR_AEDISABLED   = 0x0502;

// class rrfc
const u_int32_t jerrno::JERR_RRFC_OPENRD        = 0x0600;

// class jrec, enq_rec, deq_rec, txn_rec
const u_int32_t jerrno::JERR_JREC_BADRECHDR     = 0x0700;
const u_int32_t jerrno::JERR_JREC_BADRECTAIL    = 0x0701;

// class wmgr
const u_int32_t jerrno::JERR_WMGR_BADPGSTATE    = 0x0801;
const u_int32_t jerrno::JERR_WMGR_BADDTOKSTATE  = 0x0802;
const u_int32_t jerrno::JERR_WMGR_ENQDISCONT    = 0x0803;
const u_int32_t jerrno::JERR_WMGR_DEQDISCONT    = 0x0804;
const u_int32_t jerrno::JERR_WMGR_DEQRIDNOTENQ  = 0x0805;

// class rmgr
const u_int32_t jerrno::JERR_RMGR_UNKNOWNMAGIC  = 0x0900;
const u_int32_t jerrno::JERR_RMGR_RIDMISMATCH   = 0x0901;
//const u_int32_t jerrno::JERR_RMGR_FIDMISMATCH   = 0x0902;
const u_int32_t jerrno::JERR_RMGR_ENQSTATE      = 0x0903;
const u_int32_t jerrno::JERR_RMGR_BADRECTYPE    = 0x0904;

// class data_tok
const u_int32_t jerrno::JERR_DTOK_ILLEGALSTATE  = 0x0a00;
// const u_int32_t jerrno::JERR_DTOK_RIDNOTSET     = 0x0a01;

// class enq_map, txn_map
const u_int32_t jerrno::JERR_MAP_DUPLICATE      = 0x0b00;
const u_int32_t jerrno::JERR_MAP_NOTFOUND       = 0x0b01;
const u_int32_t jerrno::JERR_MAP_LOCKED         = 0x0b02;

// class jinf
const u_int32_t jerrno::JERR_JINF_CVALIDFAIL    = 0x0c00;
const u_int32_t jerrno::JERR_JINF_NOVALUESTR    = 0x0c01;
const u_int32_t jerrno::JERR_JINF_BADVALUESTR   = 0x0c02;
const u_int32_t jerrno::JERR_JINF_JDATEMPTY     = 0x0c03;
const u_int32_t jerrno::JERR_JINF_TOOMANYFILES  = 0x0c04;
const u_int32_t jerrno::JERR_JINF_INVALIDFHDR   = 0x0c05;
const u_int32_t jerrno::JERR_JINF_STAT          = 0x0c06;
const u_int32_t jerrno::JERR_JINF_NOTREGFILE    = 0x0c07;
const u_int32_t jerrno::JERR_JINF_BADFILESIZE   = 0x0c08;
const u_int32_t jerrno::JERR_JINF_OWIBAD        = 0x0c09;
const u_int32_t jerrno::JERR_JINF_ZEROLENFILE   = 0x0c0a;

// Negative returns for some functions
const int32_t jerrno::AIO_TIMEOUT               = -1;
const int32_t jerrno::LOCK_TAKEN                = -2;


// static initialization fn

bool
jerrno::__init()
{
    // generic errors
    _err_map[JERR__MALLOC] = "JERR__MALLOC: Buffer memory allocation failed.";
    _err_map[JERR__UNDERFLOW] = "JERR__UNDERFLOW: Underflow error";
    _err_map[JERR__NINIT] = "JERR__NINIT: Operation on uninitialized class.";
    _err_map[JERR__AIO] = "JERR__AIO: AIO error.";
    _err_map[JERR__FILEIO] = "JERR__FILEIO: File read or write failure.";
    _err_map[JERR__RTCLOCK] = "JERR__RTCLOCK: Reading real-time clock failed.";
    _err_map[JERR__PTHREAD] = "JERR__PTHREAD: pthread failure.";
    _err_map[JERR__TIMEOUT] = "JERR__TIMEOUT: Timeout waiting for event.";
    _err_map[JERR__UNEXPRESPONSE] = "JERR__UNEXPRESPONSE: Unexpected response to call or event.";
    _err_map[JERR__RECNFOUND] = "JERR__RECNFOUND: Record not found.";
    _err_map[JERR__NOTIMPL] = "JERR__NOTIMPL: Not implemented";

    // class jcntl
    _err_map[JERR_JCNTL_STOPPED] = "JERR_JCNTL_STOPPED: Operation on stopped journal.";
    _err_map[JERR_JCNTL_READONLY] = "JERR_JCNTL_READONLY: Write operation on read-only journal (during recovery).";
    _err_map[JERR_JCNTL_AIOCMPLWAIT] = "JERR_JCNTL_AIOCMPLWAIT: Timeout waiting for AIOs to complete.";
    _err_map[JERR_JCNTL_UNKNOWNMAGIC] = "JERR_JCNTL_UNKNOWNMAGIC: Found record with unknown magic.";
    _err_map[JERR_JCNTL_NOTRECOVERED] = "JERR_JCNTL_NOTRECOVERED: Operation requires recover() to be run first.";
    _err_map[JERR_JCNTL_RECOVERJFULL] = "JERR_JCNTL_RECOVERJFULL: Journal data files full, cannot write.";
    _err_map[JERR_JCNTL_OWIMISMATCH] = "JERR_JCNTL_OWIMISMATCH: Overwrite Indicator (OWI) change found in unexpected location.";

    // class jdir
    _err_map[JERR_JDIR_NOTDIR] = "JERR_JDIR_NOTDIR: Directory name exists but is not a directory.";
    _err_map[JERR_JDIR_MKDIR] = "JERR_JDIR_MKDIR: Directory creation failed.";
    _err_map[JERR_JDIR_OPENDIR] = "JERR_JDIR_OPENDIR: Directory open failed.";
    _err_map[JERR_JDIR_READDIR] = "JERR_JDIR_READDIR: Directory read failed.";
    _err_map[JERR_JDIR_CLOSEDIR] = "JERR_JDIR_CLOSEDIR: Directory close failed.";
    _err_map[JERR_JDIR_RMDIR] = "JERR_JDIR_RMDIR: Directory delete failed.";
    _err_map[JERR_JDIR_NOSUCHFILE] = "JERR_JDIR_NOSUCHFILE: File does not exist.";
    _err_map[JERR_JDIR_FMOVE] = "JERR_JDIR_FMOVE: File move failed.";
    _err_map[JERR_JDIR_STAT] = "JERR_JDIR_STAT: File stat failed.";
    _err_map[JERR_JDIR_UNLINK] = "JERR_JDIR_UNLINK: File delete failed.";
    _err_map[JERR_JDIR_BADFTYPE] = "JERR_JDIR_BADFTYPE: Bad or unknown file type (stat mode).";

    // class fcntl
    _err_map[JERR_FCNTL_OPENWR] = "JERR_FCNTL_OPENWR: Unable to open file for write.";
    _err_map[JERR_FCNTL_WRITE] = "JERR_FCNTL_WRITE: Unable to write to file.";
    _err_map[JERR_FCNTL_CLOSE] = "JERR_FCNTL_CLOSE: File close failed.";
    _err_map[JERR_FCNTL_FILEOFFSOVFL] = "JERR_FCNTL_FILEOFFSOVFL: Attempted increase file offset past file size.";
    _err_map[JERR_FCNTL_CMPLOFFSOVFL] = "JERR_FCNTL_CMPLOFFSOVFL: Attempted increase completed file offset past submitted offset.";
    _err_map[JERR_FCNTL_RDOFFSOVFL] = "JERR_FCNTL_RDOFFSOVFL: Attempted increase read offset past write offset.";

    // class lfmgr
    _err_map[JERR_LFMGR_BADAEFNUMLIM] = "JERR_LFMGR_BADAEFNUMLIM: Auto-expand file number limit lower than initial number of journal files.";
    _err_map[JERR_LFMGR_AEFNUMLIMIT] = "JERR_LFMGR_AEFNUMLIMIT: Exceeded auto-expand file number limit.";
    _err_map[JERR_LFMGR_AEDISABLED] = "JERR_LFMGR_AEDISABLED: Attempted to expand with auto-expand disabled.";

    // class rrfc
    _err_map[JERR_RRFC_OPENRD] = "JERR_RRFC_OPENRD: Unable to open file for read.";

    // class jrec, enq_rec, deq_rec, txn_rec
    _err_map[JERR_JREC_BADRECHDR] = "JERR_JREC_BADRECHDR: Invalid data record header.";
    _err_map[JERR_JREC_BADRECTAIL] = "JERR_JREC_BADRECTAIL: Invalid data record tail.";

    // class wmgr
    _err_map[JERR_WMGR_BADPGSTATE] = "JERR_WMGR_BADPGSTATE: Page buffer in illegal state for operation.";
    _err_map[JERR_WMGR_BADDTOKSTATE] = "JERR_WMGR_BADDTOKSTATE: Data token in illegal state for operation.";
    _err_map[JERR_WMGR_ENQDISCONT] = "JERR_WMGR_ENQDISCONT: Enqueued new dtok when previous enqueue returned partly completed (state ENQ_PART).";
    _err_map[JERR_WMGR_DEQDISCONT] = "JERR_WMGR_DEQDISCONT: Dequeued new dtok when previous dequeue returned partly completed (state DEQ_PART).";
    _err_map[JERR_WMGR_DEQRIDNOTENQ] = "JERR_WMGR_DEQRIDNOTENQ: Dequeue rid is not enqueued.";

    // class rmgr
    _err_map[JERR_RMGR_UNKNOWNMAGIC] = "JERR_RMGR_UNKNOWNMAGIC: Found record with unknown magic.";
    _err_map[JERR_RMGR_RIDMISMATCH] = "JERR_RMGR_RIDMISMATCH: RID mismatch between current record and dtok RID";
    //_err_map[JERR_RMGR_FIDMISMATCH] = "JERR_RMGR_FIDMISMATCH: FID mismatch between emap and rrfc";
    _err_map[JERR_RMGR_ENQSTATE] = "JERR_RMGR_ENQSTATE: Attempted read when data token wstate was not ENQ";
    _err_map[JERR_RMGR_BADRECTYPE] = "JERR_RMGR_BADRECTYPE: Attempted operation on inappropriate record type";

    // class data_tok
    _err_map[JERR_DTOK_ILLEGALSTATE] = "JERR_MTOK_ILLEGALSTATE: Attempted to change to illegal state.";
    //_err_map[JERR_DTOK_RIDNOTSET] = "JERR_DTOK_RIDNOTSET: Record ID not set.";

    // class enq_map, txn_map
    _err_map[JERR_MAP_DUPLICATE] = "JERR_MAP_DUPLICATE: Attempted to insert record into map using duplicate key.";
    _err_map[JERR_MAP_NOTFOUND] = "JERR_MAP_NOTFOUND: Key not found in map.";
    _err_map[JERR_MAP_LOCKED] = "JERR_MAP_LOCKED: Record ID locked by a pending transaction.";

    // class jinf
    _err_map[JERR_JINF_CVALIDFAIL] = "JERR_JINF_CVALIDFAIL: Journal compatibility validation failure.";
    _err_map[JERR_JINF_NOVALUESTR] = "JERR_JINF_NOVALUESTR: No value attribute found in jinf file.";
    _err_map[JERR_JINF_BADVALUESTR] = "JERR_JINF_BADVALUESTR: Bad format for value attribute in jinf file";
    _err_map[JERR_JINF_JDATEMPTY] = "JERR_JINF_JDATEMPTY: Journal data files empty.";
    _err_map[JERR_JINF_TOOMANYFILES] = "JERR_JINF_TOOMANYFILES: Too many journal data files.";
    _err_map[JERR_JINF_INVALIDFHDR] = "JERR_JINF_INVALIDFHDR: Invalid journal data file header";
    _err_map[JERR_JINF_STAT] = "JERR_JINF_STAT: Error while trying to stat a journal data file";
    _err_map[JERR_JINF_NOTREGFILE] = "JERR_JINF_NOTREGFILE: Target journal data file is not a regular file";
    _err_map[JERR_JINF_BADFILESIZE] = "JERR_JINF_BADFILESIZE: Journal data file is of incorrect or unexpected size";
    _err_map[JERR_JINF_OWIBAD] = "JERR_JINF_OWIBAD: Journal data files have inconsistent OWI flags; >1 transition found in non-auto-expand or min-size journal";
    _err_map[JERR_JINF_ZEROLENFILE] = "JERR_JINF_ZEROLENFILE: Journal info file zero length";

    //_err_map[] = "";

    return true;
}

const char*
jerrno::err_msg(const u_int32_t err_no) throw ()
{
    _err_map_itr = _err_map.find(err_no);
    if (_err_map_itr == _err_map.end())
        return "<Unknown error code>";
    return _err_map_itr->second;
}

} // namespace journal
} // namespace mrg
