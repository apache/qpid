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
 * \file JournalError.h
 */

#ifndef qpid_asyncStore_jrnl2_JournalError_hpp_
#define qpid_asyncStore_jrnl2_JournalError_hpp_

#include "qpid/asyncStore/jrnl2/Streamable.h"

#include <map>
#include <stdexcept> // std::runtime_error
#include <stdint.h> // uint32_t

// Macro definitions

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
    throw qpid::asyncStore::jrnl2::JournalError(qpid::asyncStore::jrnl2::JournalError::JERR_PTHREAD, oss.str(), cls, fn); \
    }

/**
 * \brief Macro for throwing state-machine errors related to invalid state transitions.
 *
 * \param jerrno Error number to be thrown.
 * \param target_st State into which the state machine was attemting to move.
 * \param curr_st Current state of the state machine (making transition illegal).
 * \param cls Name of class in which failure occurred.
 * \param fn Name of class function that failed.
 */
#define THROW_STATE_EXCEPTION(jerrno, target_st, curr_st, cls, fn) { \
    std::ostringstream oss; \
    oss << cls << "::" << fn << "() in state " << curr_st << " cannot be moved to state " << target_st << "."; \
    throw qpid::asyncStore::jrnl2::JournalError(jerrno, oss.str(), cls, fn); \
    }

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

/**
 * \brief Universal error and exception class for the async store. The class uses a set of error codes to indicate
 * the error or failure condition.
 */
class JournalError : public std::runtime_error, public Streamable
{
public:
    /**
     * \brief Default constructor.
     */
    JournalError() throw ();

    /**
     * \brief Constructor which accepts only the error code.
     */
    JournalError(const uint32_t errorCode) throw ();

    /**
     * \brief Constructor which accepts only additional info as a c-string.
     */
    JournalError(const char* additionalInfo) throw ();

    /**
     * \brief Constructor which accepts only additional info as a std::string.
     */
    JournalError(const std::string& additionalInfo) throw ();

    /**
     * \brief Constructor which accepts both the error code and additional info as a c-string.
     */
    JournalError(const uint32_t errorCode,
                 const char* additionalInfo) throw ();

    /**
     * \brief Constructor which accepts both the error code and additional info as a std::string.
     */
    JournalError(const uint32_t errorCode,
                 const std::string& additionalInfo) throw ();


    /**
     * \brief Constructor which accepts both the error code and the throwing class name as a c-string.
     */
    JournalError(const uint32_t errorCode,
                 const char* throwingClass,
                 const char* throwingFunction) throw ();

    /**
     * \brief Constructor which accepts both the error code and the throwing class name as a std::string.
     */
    JournalError(const uint32_t errorCode,
                 const std::string& throwingClass,
                 const std::string& throwingFunction) throw ();


    /**
     * \brief Constructor which accepts the error code, the throwing class and function as c-strings.
     */
    JournalError(const uint32_t errorCode,
                 const char* additionalInfo,
                 const char* throwingClass,
                 const char* throwingFunction) throw ();

    /**
     * \brief Constructor which accepts the error code, the throwing class and function as std::strings.
     */
    JournalError(const uint32_t errorCode,
                 const std::string& additionalInfo,
                 const std::string& throwingClass,
                 const std::string& throwingFunction) throw ();


    /**
     * \brief Destructor guaranteed not to throw.
     */
    virtual ~JournalError() throw ();

    /**
     * \brief Overloaded std::exception::what() call which returns a string representation of the error or fault.
     *
     * \returns C-string representation of the error or fault.
     */
    const char* what() const throw (); // override std::exception::what()

    /**
     * \brief Get the error or fault code.
     *
     * \returns The error or fault code.
     */
    uint32_t getErrorCode() const throw ();

    /**
     * \brief Get additional error or fault info from the \c m_additionalInfo field.
     *
     * \returns Additional error or fault info.
     */
    const std::string getAdditionalInfo() const throw ();

    /**
     * \brief Get name of throwing class from the \c m_throwingClass field.
     *
     * \returns Name of throwing class.
     */
    const std::string getThrowingClass() const throw ();

    /**
     * \brief Get name of throwing function from \c m_throwingFunction field.
     *
     * \returns Name of throwing function.
     */
    const std::string getThrowingFunction() const throw ();

    // -- Implementation of Streamable methods ---

    virtual void toStream(std::ostream&) const;

    // --- Static error codes ---

    // generic and system errors
    static const uint32_t JERR_PTHREAD;         ///< pthread operation failure
    static const uint32_t JERR_RTCLOCK;         ///< realtime clock operation failure

    // illegal states
    static const uint32_t JERR_JRNLRUNSTATE;    ///< Illegal journal run state transition
    static const uint32_t JERR_MSGOPSTATE;      ///< Illegal msg op state transition
    static const uint32_t JERR_MSGWRCMPLSTATE;  ///< Illegal msg write completion state transition

    // file ops and file i/o
    static const uint32_t JERR_STAT;            ///< Call to stat() or lstat() failed
    static const uint32_t JERR_OPENDIR;         ///< Call to opendir() failed
    static const uint32_t JERR_CLOSEDIR;        ///< Call to closedir() failed
    static const uint32_t JERR_MKDIR;           ///< Call to rmdir() failed
    static const uint32_t JERR_RMDIR;           ///< Call to rmdir() failed
    static const uint32_t JERR_UNLINK;          ///< Call to unlink() failed
    static const uint32_t JERR_NOTADIR;         ///< Not a directory
    static const uint32_t JERR_BADFTYPE;        ///< Bad file type
    static const uint32_t JERR_DIRNOTEMPTY;     ///< Directory not empty

    /**
     * \brief Static method which converts an error or exception code into a c-string representation.
     *
     * \param err_no Error code for which a string representation is desired.
     * \returns C-string representation of the error code \c err_no.
     */
    static const char* s_errorMessage(const uint32_t err_no) throw ();

private:
    uint32_t m_errorCode;            ///< Error or failure code, taken from JournalErrors.
    std::string m_additionalInfo;       ///< Additional information pertaining to the error or failure.
    std::string m_throwingClass;        ///< Name of the class throwing the error.
    std::string m_throwingFunction;     ///< Name of the function throwing the error.
    std::string m_what;                 ///< Standard error of failure message, taken from JournalErrors.

    /**
     * \brief Protected function to format the error message.
     */
    void formatWhatStr() throw ();

    /**
     * \brief Return this class name to print in error messages. Derived classes will have different definitions
     * for s_className, and thus this function will return the correct name in any child class.
     */
    virtual const char* className();

    typedef std::map<uint32_t, const char*> errorMap_t; ///< Type for map of error messages
    typedef errorMap_t::const_iterator errorMapCitr_t;  ///< Const iterator for map of error messages

    static errorMap_t s_errorMap;                       ///< Map of error messages
    static errorMapCitr_t s_errorMapIterator;           ///< Const iterator

    static const char* s_className;                     ///< Name of this class, used in formatting error messages.
    static bool s_initializedFlag;                      ///< Dummy flag, used to initialize map.

    static bool s_initialize();                         ///< Static fn for initializing static data

};

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_JournalError_hpp_
