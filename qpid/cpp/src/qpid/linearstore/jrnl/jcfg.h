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

#ifndef QPID_LEGACYSTORE_JRNL_JCFG_H
#define QPID_LEGACYSTORE_JRNL_JCFG_H

/**
* <b>Rule:</b> Data block size (JRNL_DBLK_SIZE_BYTES) MUST be a power of 2 AND
* a power of 2 factor of the disk softblock size (JRNL_SBLK_SIZE_BYTES):
* <pre>
* n * JRNL_DBLK_SIZE_BYTES == JRNL_SBLK_SIZE_BYTES (n = 1,2,4,8...)
* </pre>
*/
#define JRNL_SBLK_SIZE_BYTES           4096        /**< Disk softblock size in bytes */
#define QLS_AIO_ALIGN_BOUNDARY         JRNL_SBLK_SIZE_BYTES
#define JRNL_DBLK_SIZE_BYTES           128         /**< Data block size in bytes (CANNOT BE LESS THAN 32!) */
#define JRNL_SBLK_SIZE_DBLKS           (JRNL_SBLK_SIZE_BYTES / JRNL_DBLK_SIZE_BYTES)          /**< Disk softblock size in multiples of JRNL_DBLK_SIZE */
#define JRNL_SBLK_SIZE_KIB             (JRNL_SBLK_SIZE_BYTES / 1024) /**< Disk softblock size in KiB */
//#define JRNL_MIN_FILE_SIZE      128         ///< Min. jrnl file size in sblks (excl. file_hdr)
//#define JRNL_MAX_FILE_SIZE      4194176     ///< Max. jrnl file size in sblks (excl. file_hdr)
//#define JRNL_MIN_NUM_FILES      4           ///< Min. number of journal files
//#define JRNL_MAX_NUM_FILES      64          ///< Max. number of journal files
//#define JRNL_ENQ_THRESHOLD      80          ///< Percent full when enqueue connection will be closed
//
//#define JRNL_RMGR_PAGE_SIZE     128         ///< Journal page size in softblocks
//#define JRNL_RMGR_PAGES         16          ///< Number of pages to use in wmgr
//
#define JRNL_WMGR_DEF_PAGE_SIZE_KIB    32
#define JRNL_WMGR_DEF_PAGE_SIZE_SBLKS  (JRNL_WMGR_DEF_PAGE_SIZE_KIB / JRNL_SBLK_SIZE_KIB)          ///< Journal write page size in softblocks (default)
#define JRNL_WMGR_DEF_PAGES            32          ///< Number of pages to use in wmgr (default)
//
#define JRNL_WMGR_MAXDTOKPP            1024        ///< Max. dtoks (data blocks) per page in wmgr
#define JRNL_WMGR_MAXWAITUS            100         ///< Max. wait time (us) before submitting AIO
//
//#define JRNL_INFO_EXTENSION     "jinf"      ///< Extension for journal info files
//#define JRNL_DATA_EXTENSION     "jdat"      ///< Extension for journal data files
#define QLS_JRNL_FILE_EXTENSION        ".jrnl"     /**< Extension for journal data files */
#define QLS_TXA_MAGIC                  0x61534c51  /**< ("RHMa" in little endian) Magic for dtx abort hdrs */
#define QLS_TXC_MAGIC                  0x63534c51  /**< ("RHMc" in little endian) Magic for dtx commit hdrs */
#define QLS_DEQ_MAGIC                  0x64534c51  /**< ("QLSd" in little endian) Magic for deq rec hdrs */
#define QLS_ENQ_MAGIC                  0x65534c51  /**< ("QLSe" in little endian) Magic for enq rec hdrs */
#define QLS_FILE_MAGIC                 0x66534c51  /**< ("QLSf" in little endian) Magic for file hdrs */
#define QLS_EMPTY_MAGIC                0x78534c51  /**< ("QLSx" in little endian) Magic for empty dblk */
#define QLS_JRNL_VERSION               2           /**< Version (of file layout) */
#define QLS_JRNL_FHDR_RES_SIZE_SBLKS   1           /**< Journal file header reserved size in sblks (as defined by JRNL_SBLK_SIZE_BYTES) */
#define QLS_CLEAN_CHAR                 0xff        /**< Char used to clear empty space on disk */
//
//#define RHM_LENDIAN_FLAG 0      ///< Value of little endian flag on disk
//#define RHM_BENDIAN_FLAG 1      ///< Value of big endian flag on disk

#endif // ifndef QPID_LEGACYSTORE_JRNL_JCFG_H
