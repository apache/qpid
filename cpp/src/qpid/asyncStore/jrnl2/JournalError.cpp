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
 * \file JournalError.cpp
 */

#include "JournalError.h"

#include <iomanip> // std::setfill(), std::setw()
#include <sstream> // std::ostringstream

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

JournalError::JournalError() throw () :
        std::runtime_error(std::string()),
        m_errorCode(0)
{
    formatWhatStr();
}

JournalError::JournalError(const uint32_t errorCode) throw () :
        std::runtime_error(std::string()),
        m_errorCode(errorCode)
{
    formatWhatStr();
}

JournalError::JournalError(const char* additionalInfo) throw () :
        std::runtime_error(std::string()),
        m_errorCode(0),
        m_additionalInfo(additionalInfo)
{
    formatWhatStr();
}

JournalError::JournalError(const std::string& additionalInfo) throw () :
        std::runtime_error(std::string()),
        m_errorCode(0),
        m_additionalInfo(additionalInfo)
{
    formatWhatStr();
}

JournalError::JournalError(const uint32_t errorCode,
                           const char* additionalInfo) throw () :
        std::runtime_error(std::string()),
        m_errorCode(errorCode),
        m_additionalInfo(additionalInfo)
{
    formatWhatStr();
}

JournalError::JournalError(const uint32_t errorCode,
                           const std::string& additionalInfo) throw () :
        std::runtime_error(std::string()),
        m_errorCode(errorCode),
        m_additionalInfo(additionalInfo)
{
    formatWhatStr();
}

JournalError::JournalError(const uint32_t errorCode,
                           const char* throwingClass,
                           const char* throwingFunction) throw () :
        std::runtime_error(std::string()),
        m_errorCode(errorCode),
        m_throwingClass(throwingClass),
        m_throwingFunction(throwingFunction)
{
    formatWhatStr();
}

JournalError::JournalError(const uint32_t errorCode,
                           const std::string& throwingClass,
                           const std::string& throwingFunction) throw () :
        std::runtime_error(std::string()),
        m_errorCode(errorCode),
        m_throwingClass(throwingClass),
        m_throwingFunction(throwingFunction)
{
    formatWhatStr();
}

JournalError::JournalError(const uint32_t errorCode,
                           const char* additionalInfo,
                           const char* throwingClass,
                           const char* throwingFunction) throw () :
        std::runtime_error(std::string()),
        m_errorCode(errorCode),
        m_additionalInfo(additionalInfo),
        m_throwingClass(throwingClass),
        m_throwingFunction(throwingFunction)
{
    formatWhatStr();
}

JournalError::JournalError(const uint32_t errorCode,
                           const std::string& additionalInfo,
                           const std::string& throwingClass,
                           const std::string& throwingFunction) throw () :
        std::runtime_error(std::string()),
        m_errorCode(errorCode),
        m_additionalInfo(additionalInfo),
        m_throwingClass(throwingClass),
        m_throwingFunction(throwingFunction)
{
    formatWhatStr();
}

JournalError::~JournalError() throw () {}

const char*
JournalError::what() const throw () {
    return m_what.c_str();
}

uint32_t
JournalError::getErrorCode() const throw () {
    return m_errorCode;
}

const std::string
JournalError::getAdditionalInfo() const throw () {
    return m_additionalInfo;
}

const std::string
JournalError::getThrowingClass() const throw () {
    return m_throwingClass;
}

const std::string
JournalError::getThrowingFunction() const throw () {
    return m_throwingFunction;
}

void
JournalError::toStream(std::ostream& os) const {
    os << what();
}

// protected
void
JournalError::formatWhatStr() throw () {
    try {
        const bool ai = !m_additionalInfo.empty();
        const bool tc = !m_throwingClass.empty();
        const bool tf = !m_throwingFunction.empty();
        std::ostringstream oss;
        oss << className() << " 0x" << std::hex << std::setfill('0') << std::setw(4) << m_errorCode << " ";
        if (tc) {
            oss << m_throwingClass;
            if (tf) {
                oss << "::";
            } else {
                oss << " ";
            }
        }
        if (tf) {
            oss << m_throwingFunction << "() ";
        }
        if (tc || tf) {
            oss << "threw " << s_errorMessage(m_errorCode);
        }
        if (ai) {
            oss << " (" << m_additionalInfo << ")";
        }
        m_what.assign(oss.str());
    } catch (...) {}
}

// protected
const char*
JournalError::className() {
    return s_className;
}

// static error code definitions
JournalError::errorMap_t JournalError::s_errorMap;
JournalError::errorMapCitr_t JournalError::s_errorMapIterator;

// generic and system errors
const uint32_t JournalError::JERR_PTHREAD           = 0x0001;
const uint32_t JournalError::JERR_RTCLOCK           = 0x0002;

// illegal states
const uint32_t JournalError::JERR_JRNLRUNSTATE      = 0x0101;
const uint32_t JournalError::JERR_MSGOPSTATE        = 0x0102;
const uint32_t JournalError::JERR_MSGWRCMPLSTATE    = 0x0103;

// file ops and file i/o
const uint32_t JournalError::JERR_STAT              = 0x0200;
const uint32_t JournalError::JERR_OPENDIR           = 0x0201;
const uint32_t JournalError::JERR_CLOSEDIR          = 0x0202;
const uint32_t JournalError::JERR_MKDIR             = 0x0203;
const uint32_t JournalError::JERR_RMDIR             = 0x0204;
const uint32_t JournalError::JERR_UNLINK            = 0x0205;
const uint32_t JournalError::JERR_NOTADIR           = 0x0206;
const uint32_t JournalError::JERR_BADFTYPE          = 0x0207;
const uint32_t JournalError::JERR_DIRNOTEMPTY       = 0x0208;

// static
const char*
JournalError::s_errorMessage(const uint32_t err_no) throw () {
    s_errorMapIterator = s_errorMap.find(err_no);
    if (s_errorMapIterator == s_errorMap.end())
        return "<Unknown error code>";
    return s_errorMapIterator->second;
}

// private static
const char* JournalError::s_className = "qpid::asyncStore::jrnl2::Error";
bool JournalError::s_initializedFlag = JournalError::s_initialize();

// private static
bool
JournalError::s_initialize() {
    s_errorMap[JERR_PTHREAD] = "JERR_PTHREAD: pthread operation failure";
    s_errorMap[JERR_RTCLOCK] = "JERR_RTCLOCK: realtime clock operation failure";

    s_errorMap[JERR_JRNLRUNSTATE] = "JERR_JRNLRUNSTATE: Illegal journal run state transition";
    s_errorMap[JERR_MSGOPSTATE] = "JERR_MSGOPSTATE: Illegal msg op state transition";
    s_errorMap[JERR_MSGWRCMPLSTATE] = "JERR_MSGWRCMPLSTATE: Illegal msg write completion state transition";

    s_errorMap[JERR_STAT] = "JERR_STAT: Call to stat() (file status) failed";
    s_errorMap[JERR_OPENDIR] = "JERR_OPENDIR: Call to opendir() failed";
    s_errorMap[JERR_CLOSEDIR] = "JERR_CLOSEDIR: Call to closedir() failed";
    s_errorMap[JERR_MKDIR] = "JERR_MKDIR: Call to mkdir() failed";
    s_errorMap[JERR_RMDIR] = "JERR_RMDIR: Call to rmdir() failed";
    s_errorMap[JERR_UNLINK] = "JERR_UNLINK: Call to unlink() (file delete) failed";
    s_errorMap[JERR_NOTADIR] = "JERR_NOTADIR: Not a directory";
    s_errorMap[JERR_BADFTYPE] = "JERR_BADFTYPE: Bad file type";
    s_errorMap[JERR_DIRNOTEMPTY] = "JERR_DIRNOTEMPTY: Directory not empty";

    return true;
}

}}} // namespace qpid::asyncStore::jrnl2
