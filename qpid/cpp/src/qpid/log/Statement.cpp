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

#include "qpid/log/Statement.h"
#include "qpid/log/Logger.h"
#include <boost/bind.hpp>
#include <stdexcept>
#include <algorithm>
#include <ctype.h>

namespace qpid {
namespace log {

namespace {
struct NonPrint { bool operator()(unsigned char c) { return !isprint(c) && !isspace(c); } };

const char hex[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

std::string quote(const std::string& str) {
    NonPrint nonPrint;
    size_t n = std::count_if(str.begin(), str.end(), nonPrint);
    if (n==0) return str;
    std::string ret;
    ret.reserve(str.size()+3*n); // Avoid extra allocations.
    for (std::string::const_iterator i = str.begin(); i != str.end(); ++i) {
        if (nonPrint(*i)) {
            ret.push_back('\\');
            ret.push_back('x');
            ret.push_back(hex[((*i) >> 4)&0xf]);
            ret.push_back(hex[(*i) & 0xf]);
        }
        else ret.push_back(*i);
    }
    return ret;
}
}

void Statement::log(const std::string& message) {
    Logger::instance().log(*this, quote(message));
}

#define test(STR, CAT)           \
if (strstr(fName, (STR)) != 0) {\
    return (::qpid::log::CAT); \
}
//    std::cout << "File: " << fName << " categorized as " << CategoryTraits::name(::qpid::log::CAT) << std::endl;

Category Statement::categoryOf(const char*const fName) {
    test("AsynchIO",    io);
    test("TCP",         io);
    test("epoll",       io);
    test("Pollable",    io);
    test("Socket",      io);

    test("Sasl",        security);
    test("Ssl",         security);
    test("Acl",         security);
    test("acl",         security);
    test("cyrus",       security);

    test("amqp_",       amqp);
    test("framing",     amqp);

    test("management",  management);
    test("qmf",         management);
    test("console",     management);

    test("cluster",     ha);
    test("qpid/ha",     ha);
    test("qpid\\ha",    ha);
    test("replication", ha);
    test("ClusterSafe", ha);

    test("broker",      broker);
    test("SessionState",broker);
    test("DataDir",     broker);
    test("qpidd",       broker);

    test("store",       store);

    test("assert",      system);
    test("Exception",   system);
    test("sys",         system);
    test("SCM",         system);

    test("tests",       test);

    test("messaging",   messaging);
    test("types",       messaging);

    std::cout << "Unspecified file:" << fName << std::endl;
    return unspecified;
}


void Statement::categorize(Statement& s) {
    // given a statement and it's category
    // if the category is Unspecified then try to find a
    // better category based on the path/file function name.
    if (s.category == log::unspecified) {
        s.category = categoryOf(s.file);
    } else {
        // already has a category so leave it alone
    }
}


Statement::Initializer::Initializer(Statement& s) : statement(s) {
    // QPID-3891
    // From the given BOOST_CURRENT_FUNCTION name extract only the
    // namespace-qualified-functionName, skipping return and calling args.
    // Given:
    //   <possible return value type> qpid::name::space::Function(args)
    // Return:
    //   "qpid::name::space::Function".
    if (s.function != NULL) {
        bool         foundOParen(false);
        const char * opPtr;
        for (opPtr = s.function; *opPtr != '\0'; opPtr++) {
            if (*opPtr == '(') {
                foundOParen = true;
                break;
            }
        }

        if (foundOParen) {
            const char * bPtr = opPtr;
            for (bPtr = opPtr; bPtr > s.function; bPtr--) {
                if (bPtr[-1] == ' ') {
                    break;
                }
            }

            size_t nStoreSize = opPtr - bPtr;
            if (nStoreSize > 0) {
                // Note: the struct into which this name is stored
                // is static and is never deleted.
                char * nStore = new char[nStoreSize + 1];
                std::copy (bPtr, opPtr, nStore);
                nStore[nStoreSize] = '\0';

                s.function = nStore;
            } else {
                // Ignore zero length name
            }
        } else {
            // No name found - do nothing
        }
    } else {
        // no function-name pointer to process
    }

    Statement::categorize(s);
    Logger::instance().add(s);
}


namespace {
const char* names[LevelTraits::COUNT] = {
    "trace", "debug", "info", "notice", "warning", "error", "critical"
};

const char* catNames[CategoryTraits::COUNT] = {
    "Security", "Broker", "Management", "Amqp", "System", "HA", "Messaging",
    "Store", "IO", "Test", "Unspecified"
};

} // namespace
Level LevelTraits::level(const char* name) {
    for (int i =0; i < LevelTraits::COUNT; ++i) {
        if (strcmp(names[i], name)==0)
            return Level(i);
    }
    throw std::runtime_error(std::string("Invalid log level name: ")+name);
}

const char* LevelTraits::name(Level l) {
    return names[l];
}

bool CategoryTraits::isCategory(const std::string& name) {
    for (int i =0; i < CategoryTraits::COUNT; ++i) {
        if (strcmp(catNames[i], name.c_str())==0)
            return true;
    }
    return false;
}

Category CategoryTraits::category(const char* name) {
    for (int i =0; i < CategoryTraits::COUNT; ++i) {
        if (strcmp(catNames[i], name)==0)
            return Category(i);
    }
    throw std::runtime_error(std::string("Invalid log category name: ")+name);
}

const char* CategoryTraits::name(Category c) {
    return catNames[c];
}
}} // namespace qpid::log
