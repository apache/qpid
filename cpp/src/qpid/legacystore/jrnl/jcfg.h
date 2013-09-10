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
 * \file jcfg.h
 *
 * Qpid asynchronous store plugin library
 *
 * This file contains \#defines that control the implementation details of
 * the journal.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_JCFG_H
#define QPID_LEGACYSTORE_JRNL_JCFG_H

#if defined(__i386__) || (__arm__) /* little endian, 32 bits */
#define JRNL_LITTLE_ENDIAN
#define JRNL_32_BIT
#elif defined(__PPC__) || defined(__s390__)  /* big endian, 32 bits */
#define JRNL_BIG_ENDIAN
#define JRNL_32_BIT
#elif defined(__ia64__) || defined(__x86_64__) || defined(__alpha__) || (__arm64__) /* little endian, 64 bits */
#define JRNL_LITTLE_ENDIAN
#define JRNL_64_BIT
#elif defined(__powerpc64__) || defined(__s390x__) /* big endian, 64 bits */
#define JRNL_BIG_ENDIAN
#define JRNL_64_BIT
#else
#error endian?
#endif


/**
* <b>Rule:</b> Data block size (JRNL_DBLK_SIZE) MUST be a power of 2 such that
* <pre>
* JRNL_DBLK_SIZE * JRNL_SBLK_SIZE == n * 512 (n = 1,2,3...)
* </pre>
* (The disk softblock size is 512 for Linux kernels >= 2.6)
*/
#define JRNL_DBLK_SIZE          128         ///< Data block size in bytes (CANNOT BE LESS THAN 32!)
#define JRNL_SBLK_SIZE          4           ///< Disk softblock size in multiples of JRNL_DBLK_SIZE
#define JRNL_MIN_FILE_SIZE      128         ///< Min. jrnl file size in sblks (excl. file_hdr)
#define JRNL_MAX_FILE_SIZE      4194176     ///< Max. jrnl file size in sblks (excl. file_hdr)
#define JRNL_MIN_NUM_FILES      4           ///< Min. number of journal files
#define JRNL_MAX_NUM_FILES      64          ///< Max. number of journal files
#define JRNL_ENQ_THRESHOLD      80          ///< Percent full when enqueue connection will be closed

#define JRNL_RMGR_PAGE_SIZE     128         ///< Journal page size in softblocks
#define JRNL_RMGR_PAGES         16          ///< Number of pages to use in wmgr

#define JRNL_WMGR_DEF_PAGE_SIZE 64          ///< Journal write page size in softblocks (default)
#define JRNL_WMGR_DEF_PAGES     32          ///< Number of pages to use in wmgr (default)

#define JRNL_WMGR_MAXDTOKPP     1024        ///< Max. dtoks (data blocks) per page in wmgr
#define JRNL_WMGR_MAXWAITUS     100         ///< Max. wait time (us) before submitting AIO

#define JRNL_INFO_EXTENSION     "jinf"      ///< Extension for journal info files
#define JRNL_DATA_EXTENSION     "jdat"      ///< Extension for journal data files
#define RHM_JDAT_TXA_MAGIC      0x614d4852  ///< ("RHMa" in little endian) Magic for dtx abort hdrs
#define RHM_JDAT_TXC_MAGIC      0x634d4852  ///< ("RHMc" in little endian) Magic for dtx commit hdrs
#define RHM_JDAT_DEQ_MAGIC      0x644d4852  ///< ("RHMd" in little endian) Magic for deq rec hdrs
#define RHM_JDAT_ENQ_MAGIC      0x654d4852  ///< ("RHMe" in little endian) Magic for enq rec hdrs
#define RHM_JDAT_FILE_MAGIC     0x664d4852  ///< ("RHMf" in little endian) Magic for file hdrs
#define RHM_JDAT_EMPTY_MAGIC    0x784d4852  ///< ("RHMx" in little endian) Magic for empty dblk
#define RHM_JDAT_VERSION        0x01        ///< Version (of file layout)
#define RHM_CLEAN_CHAR          0xff        ///< Char used to clear empty space on disk

#define RHM_LENDIAN_FLAG 0      ///< Value of little endian flag on disk
#define RHM_BENDIAN_FLAG 1      ///< Value of big endian flag on disk

#endif // ifndef QPID_LEGACYSTORE_JRNL_JCFG_H
