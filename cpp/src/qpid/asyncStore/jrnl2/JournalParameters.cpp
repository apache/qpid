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
 * \file JournalParameters.cpp
 */

#include "JournalParameters.h"

#include <sstream>

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

// static declarations
std::string JournalParameters::s_defaultJrnlDir = "/tmp/store";
std::string JournalParameters::s_defaultJrnlBaseFileName = "JournalData";
uint16_t JournalParameters::s_defaultNumJrnlFiles = 8;
uint32_t JournalParameters::s_defaultJrnlFileSize_sblks = 3072;
uint16_t JournalParameters::s_defaultWriteBuffNumPgs = 32;
uint32_t JournalParameters::s_defaultWriteBuffPgSize_sblks = 128;

JournalParameters::JournalParameters() :
        m_jrnlDir(s_defaultJrnlDir),
        m_jrnlBaseFileName(s_defaultJrnlBaseFileName),
        m_numJrnlFiles(s_defaultNumJrnlFiles),
        m_jrnlFileSize_sblks(s_defaultJrnlFileSize_sblks),
        m_writeBuffNumPgs(s_defaultWriteBuffNumPgs),
        m_writeBuffPgSize_sblks(s_defaultWriteBuffPgSize_sblks)
{}

JournalParameters::JournalParameters(const std::string& jrnlDir,
                                     const std::string& jrnlBaseFileName,
                                     const uint16_t numJrnlFiles,
                                     const uint32_t jrnlFileSize_sblks,
                                     const uint16_t writeBuffNumPgs,
                                     const uint32_t writeBuffPgSize_sblks) :
        m_jrnlDir(jrnlDir),
        m_jrnlBaseFileName(jrnlBaseFileName),
        m_numJrnlFiles(numJrnlFiles),
        m_jrnlFileSize_sblks(jrnlFileSize_sblks),
        m_writeBuffNumPgs(writeBuffNumPgs),
        m_writeBuffPgSize_sblks(writeBuffPgSize_sblks)
{}

JournalParameters::JournalParameters(const JournalParameters& sp) :
        m_jrnlDir(sp.m_jrnlDir),
        m_jrnlBaseFileName(sp.m_jrnlBaseFileName),
        m_numJrnlFiles(sp.m_numJrnlFiles),
        m_jrnlFileSize_sblks(sp.m_jrnlFileSize_sblks),
        m_writeBuffNumPgs(sp.m_writeBuffNumPgs),
        m_writeBuffPgSize_sblks(sp.m_writeBuffPgSize_sblks)
{}

}}} // namespace qpid::asyncStore::jrnl2
