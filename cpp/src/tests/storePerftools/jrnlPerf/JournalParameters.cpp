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

#include <cstdlib> // std::atof, std::atoi, std::atol

namespace tests {
namespace storePerftools {
namespace jrnlPerf {

#ifndef JOURNAL2
// static declarations - for jrnl2, these are inherited
std::string JournalParameters::s_defaultJrnlDir = "/tmp/store";
std::string JournalParameters::s_defaultJrnlBaseFileName = "JournalData";
uint16_t JournalParameters::s_defaultNumJrnlFiles = 8;
uint32_t JournalParameters::s_defaultJrnlFileSize_sblks = 3072;
uint16_t JournalParameters::s_defaultWriteBuffNumPgs = 32;
uint32_t JournalParameters::s_defaultWriteBuffPgSize_sblks = 128;
#endif

JournalParameters::JournalParameters() :
#ifdef JOURNAL2
        qpid::asyncStore::jrnl2::JournalParameters()
#else
        Parameters(),
        m_jrnlDir(s_defaultJrnlDir),
        m_jrnlBaseFileName(s_defaultJrnlBaseFileName),
        m_numJrnlFiles(s_defaultNumJrnlFiles),
        m_jrnlFileSize_sblks(s_defaultJrnlFileSize_sblks),
        m_writeBuffNumPgs(s_defaultWriteBuffNumPgs),
        m_writeBuffPgSize_sblks(s_defaultWriteBuffPgSize_sblks)
#endif
{}

JournalParameters::JournalParameters(const std::string& jrnlDir,
                                     const std::string& jrnlBaseFileName,
                                     const uint16_t numJrnlFiles,
                                     const uint32_t jrnlFileSize_sblks,
                                     const uint16_t writeBuffNumPgs,
                                     const uint32_t writeBuffPgSize_sblks) :
#ifdef JOURNAL2
        qpid::asyncStore::jrnl2::JournalParameters(jrnlDir, jrnlBaseFileName, numJrnlFiles, jrnlFileSize_sblks, writeBuffNumPgs,
            writeBuffPgSize_sblks)
#else
        Parameters(),
        m_jrnlDir(jrnlDir),
        m_jrnlBaseFileName(jrnlBaseFileName),
        m_numJrnlFiles(numJrnlFiles),
        m_jrnlFileSize_sblks(jrnlFileSize_sblks),
        m_writeBuffNumPgs(writeBuffNumPgs),
        m_writeBuffPgSize_sblks(writeBuffPgSize_sblks)
#endif
{}

#ifdef JOURNAL2
JournalParameters::JournalParameters(const qpid::asyncStore::jrnl2::JournalParameters& jp) :
        qpid::asyncStore::jrnl2::JournalParameters(jp)
{}
#endif

JournalParameters::JournalParameters(const JournalParameters& jp) :
#ifdef JOURNAL2
        qpid::asyncStore::jrnl2::JournalParameters(jp)
#else
        Parameters(),
        m_jrnlDir(jp.m_jrnlDir),
        m_jrnlBaseFileName(jp.m_jrnlBaseFileName),
        m_numJrnlFiles(jp.m_numJrnlFiles),
        m_jrnlFileSize_sblks(jp.m_jrnlFileSize_sblks),
        m_writeBuffNumPgs(jp.m_writeBuffNumPgs),
        m_writeBuffPgSize_sblks(jp.m_writeBuffPgSize_sblks)
#endif
{}

JournalParameters::~JournalParameters() {}

void
JournalParameters::toStream(std::ostream& os) const {
    os << "Journal Parameters:" << std::endl;
    os << "  jrnlDir = \"" << m_jrnlDir << "\"" << std::endl;
    os << "  jrnlBaseFileName = \"" << m_jrnlBaseFileName << "\"" << std::endl;
    os << "  numJrnlFiles = " << m_numJrnlFiles << std::endl;
    os << "  jrnlFileSize_sblks = " << m_jrnlFileSize_sblks << std::endl;
    os << "  writeBuffNumPgs = " << m_writeBuffNumPgs << std::endl;
    os << "  writeBuffPgSize_sblks = " << m_writeBuffPgSize_sblks << std::endl;
}

bool
JournalParameters::parseArg(const int arg,
                            const char* optarg) {
    switch(arg) {
    case 'j':
        m_jrnlDir.assign(optarg);
        break;
    case 'b':
        m_jrnlBaseFileName.assign(optarg);
        break;
    case 'f':
        m_numJrnlFiles = uint16_t(std::atoi(optarg));
        break;
    case 's':
        m_jrnlFileSize_sblks = uint32_t(std::atol(optarg));
        break;
    case 'p':
        m_writeBuffNumPgs = uint16_t(std::atoi(optarg));
        break;
    case 'c':
        m_writeBuffPgSize_sblks = uint32_t(std::atol(optarg));
        break;
    default:
        return false;
    }
    return true;
}

// static
void
JournalParameters::printArgs(std::ostream& os) {
    os << "Journal parameters:" << std::endl;
    os << " -j --jrnl_dir:                   Store directory [\""
       << JournalParameters::s_defaultJrnlDir << "\"]" << std::endl;
    os << " -b --jrnl_base_filename:         Base name for journal files [\""
       << JournalParameters::s_defaultJrnlBaseFileName << "\"]" << std::endl;
    os << " -f --num_jfiles:                 Number of journal files ["
       << JournalParameters::s_defaultNumJrnlFiles << "]" << std::endl;
    os << " -s --jfsize_sblks:               Size of each journal file in sblks (512 byte blocks) ["
       << JournalParameters::s_defaultJrnlFileSize_sblks << "]" << std::endl;
    os << " -p --wcache_num_pages:           Number of write buffer pages ["
       << JournalParameters::s_defaultWriteBuffNumPgs << "]" << std::endl;
    os << " -c --wcache_pgsize_sblks:        Size of each write buffer page in sblks (512 byte blocks) ["
       << JournalParameters::s_defaultWriteBuffPgSize_sblks << "]" << std::endl;
}

// static
std::string
JournalParameters::shortArgs() {
    return "j:b:f:s:p:c:";
}

}}} // namespace tests::storePerftools::jrnlPerf
