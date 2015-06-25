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

#include <cmath>
#include <cstdlib>

#ifndef QPID_QLS_JRNL_JCFG_H
#define QPID_QLS_JRNL_JCFG_H

#define QLS_SBLK_SIZE_BYTES             4096        /**< Disk softblock size in bytes, should match size used on disk media */
#define QLS_AIO_ALIGN_BOUNDARY_BYTES    QLS_SBLK_SIZE_BYTES /** Memory alignment boundary used for DMA */
/**
* <b>Rule:</b> Data block size (QLS_DBLK_SIZE_BYTES) MUST be a power of 2 AND
* a power of 2 factor of the disk softblock size (QLS_SBLK_SIZE_BYTES):
* <pre>
* n * QLS_DBLK_SIZE_BYTES == QLS_SBLK_SIZE_BYTES (n = 1,2,4,8...)
* </pre>
*/
#define QLS_DBLK_SIZE_BYTES             128         /**< Data block size in bytes (CANNOT BE LESS THAN 32!) */
#define QLS_SBLK_SIZE_DBLKS             (QLS_SBLK_SIZE_BYTES / QLS_DBLK_SIZE_BYTES) /**< Disk softblock size in multiples of QLS_DBLK_SIZE_BYTES */
#define QLS_SBLK_SIZE_KIB               (QLS_SBLK_SIZE_BYTES / 1024) /**< Disk softblock size in KiB */

#define QLS_WMGR_DEF_PAGE_SIZE_KIB      32          /**< Journal write page size in KiB (default) */
#define QLS_WMGR_DEF_PAGE_SIZE_SBLKS    (QLS_WMGR_DEF_PAGE_SIZE_KIB / QLS_SBLK_SIZE_KIB) /**< Journal write page size in softblocks (default) */
#define QLS_WMGR_DEF_PAGES              32          /**< Number of pages to use in wmgr (default) */

#define QLS_WMGR_MAXDTOKPP              1024        /**< Max. dtoks (data blocks) per page in wmgr */
#define QLS_WMGR_MAXWAITUS              100         /**< Max. wait time (us) before submitting AIO */

#define QLS_JRNL_FILE_EXTENSION         ".jrnl"     /**< Extension for journal data files */
#define QLS_TXA_MAGIC                   0x61534c51  /**< ("QLSa" in little endian) Magic for dtx abort hdrs */
#define QLS_TXC_MAGIC                   0x63534c51  /**< ("QLSc" in little endian) Magic for dtx commit hdrs */
#define QLS_DEQ_MAGIC                   0x64534c51  /**< ("QLSd" in little endian) Magic for deq rec hdrs */
#define QLS_ENQ_MAGIC                   0x65534c51  /**< ("QLSe" in little endian) Magic for enq rec hdrs */
#define QLS_FILE_MAGIC                  0x66534c51  /**< ("QLSf" in little endian) Magic for file hdrs */
#define QLS_EMPTY_MAGIC                 0x78534c51  /**< ("QLSx" in little endian) Magic for empty dblk */
#define QLS_JRNL_VERSION                2           /**< Version (of file layout) */
#define QLS_JRNL_FHDR_RES_SIZE_SBLKS    1           /**< Journal file header reserved size in sblks (as defined by QLS_SBLK_SIZE_BYTES) */
#define QLS_MAX_QUEUE_NAME_LEN          (QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_BYTES) - sizeof(file_hdr_t)

#define QLS_CLEAN                                   /**< If defined, writes QLS_CLEAN_CHAR to all filled areas on disk */
#define QLS_CLEAN_CHAR                  0xff        /**< Char used to clear empty space on disk */

namespace qpid {
namespace linearstore {

    const int QLS_RAND_WIDTH = (int)(::log((RAND_MAX + 1ULL))/::log(2));
    const int QLS_RAND_SHIFT1 = 64 - QLS_RAND_WIDTH;
    const int QLS_RAND_SHIFT2 = QLS_RAND_SHIFT1 - QLS_RAND_WIDTH;
    const int QLS_RAND_MASK = (int)::pow(2, QLS_RAND_SHIFT2) - 1;

}}

#endif /* ifndef QPID_QLS_JRNL_JCFG_H */
