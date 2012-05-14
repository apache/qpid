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
 * \file PerftoolError.h
 */

#ifndef tests_storePerftools_common_PerftoolError_h_
#define tests_storePerftools_common_PerftoolError_h_

#include "Streamable.h"

#include <map>
#include <stdexcept> // std::runtime_error
#include <stdint.h> // uint32_t

// Macro definitions

#include <cstring> // std::strerror()
#include <sstream> // std::ostringstream

/**
 * \brief Macro to retrieve and format the C errno value as a string.
 *
 * \param errno Value of errno to be formatted.
 */
#define FORMAT_SYSERR(errno) " errno=" << errno << " (" << std::strerror(errno) << ")"

/**
 * \brief Macro to check for a clean pthread creation and throwing a JournalException with code JERR_PTHREAD if
 * thread creation failed.
 *
 * \param err Value or errno.
 * \param pfn Name of system call that failed.
 * \param cls Name of class in which function failed.
 * \param fn Name of class function that failed.
 */
#define PTHREAD_CHK(err, pfn, cls, fn) if(err != 0) { \
    std::ostringstream oss; \
    oss << pfn << " failed: " << FORMAT_SYSERR(err); \
    throw tests::storePerftools::common::PerftoolError(tests::storePerftools::common::PerftoolError::PERR_PTHREAD, oss.str(), cls, fn); \
    }

namespace tests {
namespace storePerftools {
namespace common {

class PerftoolError: public std::runtime_error, public Streamable
{
public:
    // --- Constructors & destructors ---
    PerftoolError(const uint32_t errCode) throw ();
    PerftoolError(const std::string& errMsg) throw ();
    PerftoolError(const uint32_t errCode,
                  const std::string& errMsg) throw ();
    PerftoolError(const uint32_t errCode,
                  const std::string& throwingClass,
                  const std::string& throwingFunction) throw ();
    PerftoolError(const std::string& errMsg,
                  const std::string& throwingClass,
                  const std::string& throwingFunction) throw ();
    PerftoolError(const uint32_t errCode,
                  const std::string& errMsg,
                  const std::string& throwingClass,
                  const std::string& throwingFunction) throw ();
    virtual ~PerftoolError() throw();

    const char* what() const throw (); // overrides std::runtime_error::what()
    uint32_t getErrorCode() const throw ();
    const std::string getAdditionalInfo() const throw ();
    const std::string getThrowingClass() const throw ();
    const std::string getThrowingFunction() const throw ();

    // --- Implementation of class Streamable ---
    virtual void toStream(std::ostream& os = std::cout) const;

    // --- Generic and system errors ---
    static const uint32_t PERR_PTHREAD;                 ///< pthread operation failure


    static const char* s_errorMessage(const uint32_t err_no) throw ();


protected:
    uint32_t m_errCode;                                 ///< Error or failure code, taken from JournalErrors.
    std::string m_errMsg;                               ///< Additional information pertaining to the error or failure.
    std::string m_throwingClass;                        ///< Name of the class throwing the error.
    std::string m_throwingFunction;                     ///< Name of the function throwing the error.
    std::string m_what;                                 ///< Standard error of failure message, taken from JournalErrors.

    void formatWhatStr() throw ();
    virtual const char* className();

    typedef std::map<uint32_t, const char*> errorMap_t; ///< Type for map of error messages
    typedef errorMap_t::const_iterator errorMapCitr_t;  ///< Const iterator for map of error messages

    static errorMap_t s_errorMap;                       ///< Map of error messages
    static errorMapCitr_t s_errorMapIterator;           ///< Const iterator

private:
    static const char* s_className;                     ///< Name of this class, used in formatting error messages.
    static bool s_initializedFlag;                      ///< Dummy flag, used to initialize map.

    PerftoolError();
    static bool s_initialize();                         ///< Static fn for initializing static data

};

}}} // namespace tests::stprePerftools::common

#endif // tests_storePerftools_common_PerftoolError_h_
