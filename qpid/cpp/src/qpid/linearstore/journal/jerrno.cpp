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

#include "qpid/linearstore/journal/jerrno.h"

namespace qpid {
namespace linearstore {
namespace journal {

std::map<uint32_t, const char*> jerrno::_err_map;
std::map<uint32_t, const char*>::iterator jerrno::_err_map_itr;
bool jerrno::_initialized = jerrno::__init();

// generic errors
const uint32_t jerrno::JERR__MALLOC              = 0x0100;
const uint32_t jerrno::JERR__UNDERFLOW           = 0x0101;
const uint32_t jerrno::JERR__NINIT               = 0x0102;
const uint32_t jerrno::JERR__AIO                 = 0x0103;
const uint32_t jerrno::JERR__FILEIO              = 0x0104;
const uint32_t jerrno::JERR__RTCLOCK             = 0x0105;
const uint32_t jerrno::JERR__PTHREAD             = 0x0106;
const uint32_t jerrno::JERR__TIMEOUT             = 0x0107;
const uint32_t jerrno::JERR__UNEXPRESPONSE       = 0x0108;
const uint32_t jerrno::JERR__RECNFOUND           = 0x0109;
const uint32_t jerrno::JERR__NOTIMPL             = 0x010a;
const uint32_t jerrno::JERR__NULL                = 0x010b;
const uint32_t jerrno::JERR__SYMLINK             = 0x010c;

// class jcntl
const uint32_t jerrno::JERR_JCNTL_STOPPED        = 0x0200;
const uint32_t jerrno::JERR_JCNTL_READONLY       = 0x0201;
const uint32_t jerrno::JERR_JCNTL_AIOCMPLWAIT    = 0x0202;
const uint32_t jerrno::JERR_JCNTL_UNKNOWNMAGIC   = 0x0203;
const uint32_t jerrno::JERR_JCNTL_NOTRECOVERED   = 0x0204;
const uint32_t jerrno::JERR_JCNTL_ENQSTATE       = 0x0207;
const uint32_t jerrno::JERR_JCNTL_INVALIDENQHDR  = 0x0208;

// class jdir
const uint32_t jerrno::JERR_JDIR_NOTDIR          = 0x0300;
const uint32_t jerrno::JERR_JDIR_MKDIR           = 0x0301;
const uint32_t jerrno::JERR_JDIR_OPENDIR         = 0x0302;
const uint32_t jerrno::JERR_JDIR_READDIR         = 0x0303;
const uint32_t jerrno::JERR_JDIR_CLOSEDIR        = 0x0304;
const uint32_t jerrno::JERR_JDIR_RMDIR           = 0x0305;
const uint32_t jerrno::JERR_JDIR_NOSUCHFILE      = 0x0306;
const uint32_t jerrno::JERR_JDIR_FMOVE           = 0x0307;
const uint32_t jerrno::JERR_JDIR_STAT            = 0x0308;
const uint32_t jerrno::JERR_JDIR_UNLINK          = 0x0309;
const uint32_t jerrno::JERR_JDIR_BADFTYPE        = 0x030a;

// class JournalFile
const uint32_t jerrno::JERR_JNLF_OPEN            = 0x0400;
const uint32_t jerrno::JERR_JNLF_CLOSE           = 0x0401;
const uint32_t jerrno::JERR_JNLF_FILEOFFSOVFL    = 0x0402;
const uint32_t jerrno::JERR_JNLF_CMPLOFFSOVFL    = 0x0403;

// class LinearFileController
const uint32_t jerrno::JERR_LFCR_SEQNUMNOTFOUND  = 0x0500;

// class jrec, enq_rec, deq_rec, txn_rec
const uint32_t jerrno::JERR_JREC_BADRECHDR       = 0x0700;
const uint32_t jerrno::JERR_JREC_BADRECTAIL      = 0x0701;

// class wmgr
const uint32_t jerrno::JERR_WMGR_BADPGSTATE      = 0x0801;
const uint32_t jerrno::JERR_WMGR_BADDTOKSTATE    = 0x0802;
const uint32_t jerrno::JERR_WMGR_ENQDISCONT      = 0x0803;
const uint32_t jerrno::JERR_WMGR_DEQDISCONT      = 0x0804;
const uint32_t jerrno::JERR_WMGR_DEQRIDNOTENQ    = 0x0805;
const uint32_t jerrno::JERR_WMGR_BADFH           = 0x0806;
const uint32_t jerrno::JERR_WMGR_NOTSBLKALIGNED  = 0x0807;

// class RecoveryManager
const uint32_t jerrno::JERR_RCVM_OPENRD          = 0x0900;
const uint32_t jerrno::JERR_RCVM_STREAMBAD       = 0x0901;
const uint32_t jerrno::JERR_RCVM_READ            = 0x0902;
const uint32_t jerrno::JERR_RCVM_WRITE           = 0x0903;
const uint32_t jerrno::JERR_RCVM_NULLXID         = 0x0904;
const uint32_t jerrno::JERR_RCVM_NOTDBLKALIGNED  = 0x0905;
const uint32_t jerrno::JERR_RCVM_NULLFID         = 0x0907;
const uint32_t jerrno::JERR_RCVM_INVALIDEFPID    = 0x0908;

// class data_tok
const uint32_t jerrno::JERR_DTOK_ILLEGALSTATE    = 0x0a00;
// const uint32_t jerrno::JERR_DTOK_RIDNOTSET     = 0x0a01;

// class enq_map, txn_map
const uint32_t jerrno::JERR_MAP_DUPLICATE        = 0x0b00;
const uint32_t jerrno::JERR_MAP_NOTFOUND         = 0x0b01;
const uint32_t jerrno::JERR_MAP_LOCKED           = 0x0b02;

// EFP errors
const uint32_t jerrno::JERR_EFP_BADPARTITIONNAME = 0x0d01;
const uint32_t jerrno::JERR_EFP_BADPARTITIONDIR  = 0x0d02;
const uint32_t jerrno::JERR_EFP_BADEFPDIRNAME    = 0x0d03;
const uint32_t jerrno::JERR_EFP_NOEFP            = 0x0d04;
const uint32_t jerrno::JERR_EFP_EMPTY            = 0x0d05;
const uint32_t jerrno::JERR_EFP_LSTAT            = 0x0d06;
const uint32_t jerrno::JERR_EFP_BADFILETYPE      = 0x0d07;
const uint32_t jerrno::JERR_EFP_FOPEN            = 0x0d08;
const uint32_t jerrno::JERR_EFP_FWRITE           = 0x0d09;
const uint32_t jerrno::JERR_EFP_MKDIR            = 0x0d0a;

// Negative returns for some functions
const int32_t jerrno::AIO_TIMEOUT                = -1;
const int32_t jerrno::LOCK_TAKEN                 = -2;


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
    _err_map[JERR__NULL] = "JERR__NULL: Operation on null pointer";
    _err_map[JERR__SYMLINK] = "JERR__SYMLINK: Symbolic link operation failed";

    // class jcntl
    _err_map[JERR_JCNTL_STOPPED] = "JERR_JCNTL_STOPPED: Operation on stopped journal.";
    _err_map[JERR_JCNTL_READONLY] = "JERR_JCNTL_READONLY: Write operation on read-only journal (during recovery).";
    _err_map[JERR_JCNTL_AIOCMPLWAIT] = "JERR_JCNTL_AIOCMPLWAIT: Timeout waiting for AIOs to complete.";
    _err_map[JERR_JCNTL_UNKNOWNMAGIC] = "JERR_JCNTL_UNKNOWNMAGIC: Found record with unknown magic.";
    _err_map[JERR_JCNTL_NOTRECOVERED] = "JERR_JCNTL_NOTRECOVERED: Operation requires recover() to be run first.";
    _err_map[JERR_JCNTL_ENQSTATE] = "JERR_JCNTL_ENQSTATE: Read error: Record not in ENQ state";
    _err_map[JERR_JCNTL_INVALIDENQHDR] = "JERR_JCNTL_INVALIDENQHDR: Invalid ENQ header";

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

    // class JournalFile
    _err_map[JERR_JNLF_OPEN] = "JERR_JNLF_OPEN: Unable to open file for write";
    _err_map[JERR_JNLF_CLOSE] = "JERR_JNLF_CLOSE: Unable to close file";
    _err_map[JERR_JNLF_FILEOFFSOVFL] = "JERR_JNLF_FILEOFFSOVFL: Attempted to increase submitted offset past file size.";
    _err_map[JERR_JNLF_CMPLOFFSOVFL] = "JERR_JNLF_CMPLOFFSOVFL: Attempted to increase completed file offset past submitted offset.";

    // class LinearFileController
    _err_map[JERR_LFCR_SEQNUMNOTFOUND] = "JERR_LFCR_SEQNUMNOTFOUND: File sequence number not found";

    // class jrec, enq_rec, deq_rec, txn_rec
    _err_map[JERR_JREC_BADRECHDR] = "JERR_JREC_BADRECHDR: Invalid record header.";
    _err_map[JERR_JREC_BADRECTAIL] = "JERR_JREC_BADRECTAIL: Invalid record tail.";

    // class wmgr
    _err_map[JERR_WMGR_BADPGSTATE] = "JERR_WMGR_BADPGSTATE: Page buffer in illegal state for operation.";
    _err_map[JERR_WMGR_BADDTOKSTATE] = "JERR_WMGR_BADDTOKSTATE: Data token in illegal state for operation.";
    _err_map[JERR_WMGR_ENQDISCONT] = "JERR_WMGR_ENQDISCONT: Enqueued new dtok when previous enqueue returned partly completed (state ENQ_PART).";
    _err_map[JERR_WMGR_DEQDISCONT] = "JERR_WMGR_DEQDISCONT: Dequeued new dtok when previous dequeue returned partly completed (state DEQ_PART).";
    _err_map[JERR_WMGR_DEQRIDNOTENQ] = "JERR_WMGR_DEQRIDNOTENQ: Dequeue rid is not enqueued.";
    _err_map[JERR_WMGR_BADFH] = "JERR_WMGR_BADFH: Bad file handle.";
    _err_map[JERR_WMGR_NOTSBLKALIGNED] = "JERR_WMGR_NOTSBLKALIGNED: Offset is not soft block (sblk)-aligned";

    // class RecoveryManager
    _err_map[JERR_RCVM_OPENRD] = "JERR_RCVM_OPENRD: Unable to open file for read";
    _err_map[JERR_RCVM_STREAMBAD] = "JERR_RCVM_STREAMBAD: Read/write stream error";
    _err_map[JERR_RCVM_READ] = "JERR_RCVM_READ: Read error: no or insufficient data to read";
    _err_map[JERR_RCVM_WRITE] = "JERR_RCVM_WRITE: Write error";
    _err_map[JERR_RCVM_NULLXID] = "JERR_RCVM_NULLXID: Null XID when XID length non-null in header";
    _err_map[JERR_RCVM_NOTDBLKALIGNED] = "JERR_RCVM_NOTDBLKALIGNED: Offset is not data block (dblk)-aligned";
    _err_map[JERR_RCVM_NULLFID] = "JERR_RCVM_NULLFID: Null file id (FID)";
    _err_map[JERR_RCVM_INVALIDEFPID] = "JERR_RCVM_INVALIDEFPID: Invalid EFP identity (partition/size)";

    // class data_tok
    _err_map[JERR_DTOK_ILLEGALSTATE] = "JERR_MTOK_ILLEGALSTATE: Attempted to change to illegal state.";
    //_err_map[JERR_DTOK_RIDNOTSET] = "JERR_DTOK_RIDNOTSET: Record ID not set.";

    // class enq_map, txn_map
    _err_map[JERR_MAP_DUPLICATE] = "JERR_MAP_DUPLICATE: Attempted to insert record into map using duplicate key.";
    _err_map[JERR_MAP_NOTFOUND] = "JERR_MAP_NOTFOUND: Key not found in map.";
    _err_map[JERR_MAP_LOCKED] = "JERR_MAP_LOCKED: Record ID locked by a pending transaction.";

    // EFP errors
    _err_map[JERR_EFP_BADPARTITIONNAME] = "JERR_EFP_BADPARTITIONNAME: Invalid partition name (must be \'pNNN\' where NNN is a non-zero number)";
    _err_map[JERR_EFP_BADEFPDIRNAME] = "JERR_EFP_BADEFPDIRNAME: Bad Empty File Pool directory name (must be \'NNNk\', where NNN is a number which is a multiple of 4)";
    _err_map[JERR_EFP_BADPARTITIONDIR] = "JERR_EFP_BADPARTITIONDIR: Invalid partition directory";
    _err_map[JERR_EFP_NOEFP] = "JERR_EFP_NOEFP: No Empty File Pool found for given partition and empty file size";
    _err_map[JERR_EFP_EMPTY] = "JERR_EFP_EMPTY: Empty File Pool is empty";
    _err_map[JERR_EFP_LSTAT] = "JERR_EFP_LSTAT: lstat() operation failed";
    _err_map[JERR_EFP_BADFILETYPE] = "JERR_EFP_BADFILETYPE: File type incorrect for operation";
    _err_map[JERR_EFP_FOPEN] = "JERR_EFP_FOPEN: Unable to fopen file for write";
    _err_map[JERR_EFP_FWRITE] = "JERR_EFP_FWRITE: Write failed";
    _err_map[JERR_EFP_MKDIR] = "JERR_EFP_MKDIR: Directory creation failed";

    //_err_map[] = "";

    return true;
}

const char*
jerrno::err_msg(const uint32_t err_no) throw ()
{
    _err_map_itr = _err_map.find(err_no);
    if (_err_map_itr == _err_map.end())
        return "<Unknown error code>";
    return _err_map_itr->second;
}

}}}
