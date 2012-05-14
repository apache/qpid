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
 * \file PerftoolError.cpp
 */

#include "PerftoolError.h"

#include <iomanip> // std::setfill(), std::setw()

namespace tests {
namespace storePerftools {
namespace common {

// private
PerftoolError::PerftoolError() :
        std::runtime_error(std::string())
{}

PerftoolError::PerftoolError(const uint32_t errCode) throw () :
        std::runtime_error(std::string()),
        m_errCode(errCode)
{
    formatWhatStr();
}

PerftoolError::PerftoolError(const std::string& errMsg) throw () :
        std::runtime_error(std::string()),
        m_errCode(0),
        m_errMsg(errMsg)
{
    formatWhatStr();
}

PerftoolError::PerftoolError(const uint32_t errCode,
                             const std::string& errMsg) throw () :
        std::runtime_error(std::string()),
        m_errCode(errCode),
        m_errMsg(errMsg)
{
    formatWhatStr();
}

PerftoolError::PerftoolError(const uint32_t errCode,
                             const std::string& throwingClass,
                             const std::string& throwingFunction) throw () :
        std::runtime_error(std::string()),
        m_errCode(errCode),
        m_throwingClass(throwingClass),
        m_throwingFunction(throwingFunction)
{
    formatWhatStr();
}

PerftoolError::PerftoolError(const std::string& errMsg,
                             const std::string& throwingClass,
                             const std::string& throwingFunction) throw () :
        std::runtime_error(std::string()),
        m_errCode(0),
        m_errMsg(errMsg),
        m_throwingClass(throwingClass),
        m_throwingFunction(throwingFunction)
{
    formatWhatStr();
}

PerftoolError::PerftoolError(const uint32_t errCode,
                             const std::string& errMsg,
                             const std::string& throwingClass,
                             const std::string& throwingFunction) throw () :
        std::runtime_error(std::string()),
        m_errCode(errCode),
        m_errMsg(errMsg),
        m_throwingClass(throwingClass),
        m_throwingFunction(throwingFunction)
{}

PerftoolError::~PerftoolError() throw()
{}

const char*
PerftoolError::what() const throw ()
{
    return m_what.c_str();
}

uint32_t
PerftoolError::getErrorCode() const throw ()
{
    return m_errCode;
}

const std::string
PerftoolError::getAdditionalInfo() const throw ()
{
    return m_errMsg;
}

const std::string
PerftoolError::getThrowingClass() const throw ()
{
    return m_throwingClass;
}

const std::string
PerftoolError::getThrowingFunction() const throw ()
{
    return m_throwingFunction;
}

void
PerftoolError::toStream(std::ostream& os) const
{
    os << what();
}

// protected
void
PerftoolError::formatWhatStr() throw ()
{
    try {
        const bool ai = !m_errMsg.empty();
        const bool tc = !m_throwingClass.empty();
        const bool tf = !m_throwingFunction.empty();
        std::ostringstream oss;
        oss << className() << " 0x" << std::hex << std::setfill('0') << std::setw(4) << m_errCode << " ";
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
            oss << "threw " << s_errorMessage(m_errCode);
        }
        if (ai) {
            oss << " (" << m_errMsg << ")";
        }
        m_what.assign(oss.str());
    } catch (...) {}
}

// protected
const char*
PerftoolError::className()
{
    return s_className;
}

//static
const char* PerftoolError::s_className = "PerftoolError";

// --- Static definitions ---
PerftoolError::errorMap_t PerftoolError::s_errorMap;
PerftoolError::errorMapCitr_t PerftoolError::s_errorMapIterator;
bool PerftoolError::s_initializedFlag = PerftoolError::s_initialize();

// --- Generic and system errors ---
const uint32_t PerftoolError::PERR_PTHREAD          = 0x0001;

// static
const char*
PerftoolError::s_errorMessage(const uint32_t err_no) throw ()
{
    s_errorMapIterator = s_errorMap.find(err_no);
    if (s_errorMapIterator == s_errorMap.end())
        return "<Unknown error code>";
    return s_errorMapIterator->second;
}

// protected static
bool
PerftoolError::s_initialize()
{
    s_errorMap[PERR_PTHREAD] = "ERR_PTHREAD: pthread operation failure";

    return true;
}

}}} // namespace tests::storePerftools::common
