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

#ifndef QPID_LINEARSTORE_JOURNAL_JEXCEPTION_H
#define QPID_LINEARSTORE_JOURNAL_JEXCEPTION_H

namespace qpid {
namespace linearstore {
namespace journal {
class jexception;
}}}

#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <exception>
#include "qpid/linearstore/journal/jerrno.h"
#include <sstream>
#include <string>

// Macro for formatting commom system errors
#define FORMAT_SYSERR(errno) " errno=" << errno << " (" << std::strerror(errno) << ")"

#define MALLOC_CHK(ptr, var, cls, fn) if(ptr == 0) { \
    clean(); \
    std::ostringstream oss; \
    oss << var << ": malloc() failed: " << FORMAT_SYSERR(errno); \
    throw jexception(jerrno::JERR__MALLOC, oss.str(), cls, fn); \
    }

// TODO: The following is a temporary bug-tracking aid which forces a core.
// Replace with the commented out version below when BZ484048 is resolved.
#define PTHREAD_CHK(err, pfn, cls, fn) if(err != 0) { \
    std::ostringstream oss; \
    oss << cls << "::" << fn << "(): " << pfn; \
    errno = err; \
    ::perror(oss.str().c_str()); \
    ::abort(); \
    }
/*
#define PTHREAD_CHK(err, pfn, cls, fn) if(err != 0) { \
    std::ostringstream oss; \
    oss << pfn << " failed: " << FORMAT_SYSERR(err); \
    throw jexception(jerrno::JERR__PTHREAD, oss.str(), cls, fn); \
    }
*/

#define ASSERT(cond, msg) if(cond == 0) { \
    std::cerr << msg << std::endl; \
    ::abort(); \
    }

namespace qpid {
namespace linearstore {
namespace journal {

    /**
    * \class jexception
    * \brief Generic journal exception class
    */
    class jexception : public std::exception
    {
    private:
        uint32_t _err_code;
        std::string _additional_info;
        std::string _throwing_class;
        std::string _throwing_fn;
        std::string _what;
        void format();

    public:
        jexception() throw ();

        jexception(const uint32_t err_code) throw ();

        jexception(const char* additional_info) throw ();
        jexception(const std::string& additional_info) throw ();

        jexception(const uint32_t err_code, const char* additional_info) throw ();
        jexception(const uint32_t err_code, const std::string& additional_info) throw ();

        jexception(const uint32_t err_code, const char* throwing_class, const char* throwing_fn)
                throw ();
        jexception(const uint32_t err_code, const std::string& throwing_class,
                const std::string& throwing_fn) throw ();

        jexception(const uint32_t err_code, const char* additional_info,
                const char* throwing_class, const char* throwing_fn) throw ();
        jexception(const uint32_t err_code, const std::string& additional_info,
                const std::string& throwing_class, const std::string& throwing_fn) throw ();

        virtual ~jexception() throw ();
        virtual const char* what() const throw (); // override std::exception::what()

        inline uint32_t err_code() const throw () { return _err_code; }
        inline const std::string additional_info() const throw () { return _additional_info; }
        inline const std::string throwing_class() const throw () { return _throwing_class; }
        inline const std::string throwing_fn() const throw () { return _throwing_fn; }

        friend std::ostream& operator<<(std::ostream& os, const jexception& je);
        friend std::ostream& operator<<(std::ostream& os, const jexception* jePtr);
    }; // class jexception

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_JEXCEPTION_H
