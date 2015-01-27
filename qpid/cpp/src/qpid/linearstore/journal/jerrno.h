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

#ifndef QPID_LINEARSTORE_JOURNAL_JERRNO_H
#define QPID_LINEARSTORE_JOURNAL_JERRNO_H

namespace qpid {
namespace linearstore {
namespace journal {
class jerrno;
}}}

#include <map>
#include <stdint.h>
#include <string>

namespace qpid {
namespace linearstore {
namespace journal {

    /**
    * \class jerrno
    * \brief Class containing static error definitions and static map for error messages.
    */
    class jerrno
    {
        static std::map<uint32_t, const char*> _err_map; ///< Map of error messages
        static std::map<uint32_t, const char*>::iterator _err_map_itr; ///< Iterator
        static bool _initialized;                       ///< Dummy flag, used to initialise map.

    public:
        // generic errors
        static const uint32_t JERR__MALLOC;             ///< Buffer memory allocation failed
        static const uint32_t JERR__UNDERFLOW;          ///< Underflow error
        static const uint32_t JERR__NINIT;              ///< Operation on uninitialized class
        static const uint32_t JERR__AIO;                ///< AIO failure
        static const uint32_t JERR__FILEIO;             ///< File read or write failure
        static const uint32_t JERR__RTCLOCK;            ///< Reading real-time clock failed
        static const uint32_t JERR__PTHREAD;            ///< pthread failure
        static const uint32_t JERR__TIMEOUT;            ///< Timeout waiting for an event
        static const uint32_t JERR__UNEXPRESPONSE;      ///< Unexpected response to call or event
        static const uint32_t JERR__RECNFOUND;          ///< Record not found
        static const uint32_t JERR__NOTIMPL;            ///< Not implemented
        static const uint32_t JERR__NULL;               ///< Operation on null pointer
        static const uint32_t JERR__SYMLINK;            ///< Symbolic Link operation failed

        // class jcntl
        static const uint32_t JERR_JCNTL_STOPPED;       ///< Operation on stopped journal
        static const uint32_t JERR_JCNTL_READONLY;      ///< Write operation on read-only journal
        static const uint32_t JERR_JCNTL_AIOCMPLWAIT;   ///< Timeout waiting for AIOs to complete
        static const uint32_t JERR_JCNTL_UNKNOWNMAGIC;  ///< Found record with unknown magic
        static const uint32_t JERR_JCNTL_NOTRECOVERED;  ///< Req' recover() to be called first
        static const uint32_t JERR_JCNTL_ENQSTATE;      ///< Read error: Record not in ENQ state
        static const uint32_t JERR_JCNTL_INVALIDENQHDR; ///< Invalid ENQ header

        // class jdir
        static const uint32_t JERR_JDIR_NOTDIR;         ///< Exists but is not a directory
        static const uint32_t JERR_JDIR_MKDIR;          ///< Directory creation failed
        static const uint32_t JERR_JDIR_OPENDIR;        ///< Directory open failed
        static const uint32_t JERR_JDIR_READDIR;        ///< Directory read failed
        static const uint32_t JERR_JDIR_CLOSEDIR;       ///< Directory close failed
        static const uint32_t JERR_JDIR_RMDIR;          ///< Directory delete failed
        static const uint32_t JERR_JDIR_NOSUCHFILE;     ///< File does not exist
        static const uint32_t JERR_JDIR_FMOVE;          ///< File move failed
        static const uint32_t JERR_JDIR_STAT;           ///< File stat failed
        static const uint32_t JERR_JDIR_UNLINK;         ///< File delete failed
        static const uint32_t JERR_JDIR_BADFTYPE;       ///< Bad or unknown file type (stat mode)

        // class JournalFile
        static const uint32_t JERR_JNLF_OPEN;           ///< Unable to open file for write
        static const uint32_t JERR_JNLF_CLOSE;          ///< Unable to close file
        static const uint32_t JERR_JNLF_FILEOFFSOVFL;   ///< Increased offset past file size
        static const uint32_t JERR_JNLF_CMPLOFFSOVFL;   ///< Increased cmpl offs past subm offs

        // class LinearFileController
        static const uint32_t JERR_LFCR_SEQNUMNOTFOUND; ///< File sequence number not found

        // class jrec, enq_rec, deq_rec, txn_rec
        static const uint32_t JERR_JREC_BADRECHDR;      ///< Invalid data record header
        static const uint32_t JERR_JREC_BADRECTAIL;     ///< Invalid data record tail

        // class wmgr
        static const uint32_t JERR_WMGR_BADPGSTATE;     ///< Page buffer in illegal state.
        static const uint32_t JERR_WMGR_BADDTOKSTATE;   ///< Data token in illegal state.
        static const uint32_t JERR_WMGR_ENQDISCONT;     ///< Enq. new dtok when previous part compl.
        static const uint32_t JERR_WMGR_DEQDISCONT;     ///< Deq. new dtok when previous part compl.
        static const uint32_t JERR_WMGR_DEQRIDNOTENQ;   ///< Deq. rid not enqueued
        static const uint32_t JERR_WMGR_BADFH;          ///< Bad file handle
        static const uint32_t JERR_WMGR_NOTSBLKALIGNED; ///< Offset is not soft block (sblk)-aligned

        // class RecoveryManager
        static const uint32_t JERR_RCVM_OPENRD;         ///< Unable to open file for read
        static const uint32_t JERR_RCVM_STREAMBAD;      ///< Read/write stream error
        static const uint32_t JERR_RCVM_READ;           ///< Read error: no or insufficient data to read
        static const uint32_t JERR_RCVM_WRITE;          ///< Write error
        static const uint32_t JERR_RCVM_NULLXID;        ///< Null XID when XID length non-null in header
        static const uint32_t JERR_RCVM_NOTDBLKALIGNED; ///< Offset is not data block (dblk)-aligned
        static const uint32_t JERR_RCVM_NULLFID;        ///< Null file ID (FID)
        static const uint32_t JERR_RCVM_INVALIDEFPID;   ///< Invalid EFP identity (partition/size)

        // class data_tok
        static const uint32_t JERR_DTOK_ILLEGALSTATE;   ///< Attempted to change to illegal state
//         static const uint32_t JERR_DTOK_RIDNOTSET;   ///< Record ID not set

        // class enq_map, txn_map
        static const uint32_t JERR_MAP_DUPLICATE;       ///< Attempted to insert using duplicate key
        static const uint32_t JERR_MAP_NOTFOUND;        ///< Key not found in map
        static const uint32_t JERR_MAP_LOCKED;          ///< rid locked by pending txn

        // EFP errors
        static const uint32_t JERR_EFP_BADPARTITIONNAME;  ///< Partition name invalid or of value 0
        static const uint32_t JERR_EFP_BADEFPDIRNAME;   ///< Empty File Pool directory name invalid
        static const uint32_t JERR_EFP_BADPARTITIONDIR; ///< Invalid partition directory
        static const uint32_t JERR_EFP_NOEFP;           ///< No EFP found for given partition and file size
        static const uint32_t JERR_EFP_EMPTY;           ///< EFP empty
        static const uint32_t JERR_EFP_LSTAT;           ///< lstat operation failed
        static const uint32_t JERR_EFP_BADFILETYPE;     ///< Bad file type
        static const uint32_t JERR_EFP_FOPEN;           ///< Unable to fopen file for write
        static const uint32_t JERR_EFP_FWRITE;          ///< Write failed
        static const uint32_t JERR_EFP_MKDIR;           ///< Directory creation failed

        // Negative returns for some functions
        static const int32_t AIO_TIMEOUT;               ///< Timeout waiting for AIO return
        static const int32_t LOCK_TAKEN;                ///< Attempted to take lock, but it was taken by another thread
        /**
        * \brief Method to access error message from known error number.
        */
        static const char* err_msg(const uint32_t err_no) throw ();

    private:
        /**
        * \brief Static function to initialize map.
        */
        static bool __init();
    };

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_JERRNO_H
