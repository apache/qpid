#ifndef QPID_SYS_REGEX_H
#define QPID_SYS_REGEX_H

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#if defined(_POSIX_SOURCE) || defined(__unix__)
# include <stdexcept>
# include <string>
# include <regex.h>
#elif defined(_MSC_VER)
# include <regex>
#else
#error "No known regex implementation"
#endif

// This is a very simple wrapper for Basic Regular Expression facilities either
// provided by POSIX or C++ tr1
//
// Matching expressions are not supported and so the only useful operation is
// a simple boolean match indicator.

namespace qpid {
namespace sys {

#if defined(_POSIX_SOURCE) || defined(__unix__)

class regex {
    ::regex_t re;

public:
    regex(const std::string& s) {
        int rc = ::regcomp(&re, s.c_str(), REG_NOSUB);
        if (rc != 0) throw std::logic_error("Regular expression compilation error");
    }

    ~regex() {
        ::regfree(&re);
    }

    friend bool regex_match(const std::string& s, const regex& re);
};

inline bool regex_match(const std::string& s, const regex& re) {
    return ::regexec(&(re.re), s.c_str(), 0, 0, 0)==0;
}

#elif defined(_MSC_VER)

class regex : public std::tr1::regex {
public:
    regex(const std::string& s) :
        std::tr1::regex(s, std::tr1::regex::basic)
    {}
};

using std::tr1::regex_match;

#else
#error "No known regex implementation"
#endif

}}


#endif
