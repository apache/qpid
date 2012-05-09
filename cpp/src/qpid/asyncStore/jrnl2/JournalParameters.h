/*
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
 */

/**
 * \file JournalParameters.h
 */

#ifndef qpid_asyncStore_jrnl2_JournalParameters_h_
#define qpid_asyncStore_jrnl2_JournalParameters_h_

#include <string>
#include <stdint.h> // uint16_t, uint32_t

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

/**
 * \brief Class which encapsulates journal settings and options for one AsyncJournal instance.
 *
 * This class is a convenience encapsulation of a multitude of per-journal settings. An instance of this class
 * cannot be shared between journal instances as there must be either a different journal directory or a different
 * base file name (or both).
 */
class JournalParameters
{
public:
    // static default store params
    static std::string s_defaultJrnlDir;                ///< Default journal directory
    static std::string s_defaultJrnlBaseFileName;       ///< Default base file name for all journal files
    static uint16_t s_defaultNumJrnlFiles;              ///< Default number of journal files
    static uint32_t s_defaultJrnlFileSize_sblks;        ///< Default journal file size (in sblks)
    static uint16_t s_defaultWriteBuffNumPgs;           ///< Default number of write buffer pages
    static uint32_t s_defaultWriteBuffPgSize_sblks;     ///< Default write buffer page size (in sblks)

    std::string m_jrnlDir;                              ///< Journal directory
    std::string m_jrnlBaseFileName;                     ///< Base file name for all journal files
    uint16_t m_numJrnlFiles;                            ///< Number of journal files
    uint32_t m_jrnlFileSize_sblks;                      ///< Journal file size (in sblks)
    uint16_t m_writeBuffNumPgs;                         ///< Number of write buffer pages
    uint32_t m_writeBuffPgSize_sblks;                   ///< Write buffer page size (in dblks)

    /**
     * \brief Default constructor. This will set all members to their default settings.
     */
    JournalParameters();

    /**
     * \brief Explicit constructor, in which all settings must be supplied.
     *
     * \param jrnlDir Journal directory
     * \param jrnlBaseFileName Base file name for all journal files
     * \param numJrnlFiles Number of journal files
     * \param jrnlFileSize_sblks Journal file size (in sblks)
     * \param writeBuffNumPgs Number of write buffer pages
     * \param writeBuffPgSize_sblks Write buffer page size (in dblks)
     */
    JournalParameters(const std::string& jrnlDir,
                      const std::string& jrnlBaseFileName,
                      const uint16_t numJrnlFiles,
                      const uint32_t jrnlFileSize_sblks,
                      const uint16_t writeBuffNumPgs,
                      const uint32_t writeBuffPgSize_sblks);

    /**
     * \brief Copy constructor.
     *
     * \param sp Ref to JournalParameters instance to be copied.
     */
    JournalParameters(const JournalParameters& sp);

};

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_JournalParameters_h_
