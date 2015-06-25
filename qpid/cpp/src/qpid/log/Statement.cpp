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
#include <list>
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

//
// Instance of name hints
//
class CategoryFileNameHints {
public:
    CategoryFileNameHints(){
        hintList.push_back(std::make_pair("AsynchIo",    network));
        hintList.push_back(std::make_pair("TCP",         network));
        hintList.push_back(std::make_pair("epoll",       network));
        hintList.push_back(std::make_pair("Pollable",    network));
        hintList.push_back(std::make_pair("Socket",      network));

        hintList.push_back(std::make_pair("Sasl",        security));
        hintList.push_back(std::make_pair("Ssl",         security));
        hintList.push_back(std::make_pair("Acl",         security));
        hintList.push_back(std::make_pair("acl",         security));
        hintList.push_back(std::make_pair("cyrus",       security));

        hintList.push_back(std::make_pair("amqp_",       protocol));
        hintList.push_back(std::make_pair("framing",     protocol));

        hintList.push_back(std::make_pair("management",  management));
        hintList.push_back(std::make_pair("qmf",         management));
        hintList.push_back(std::make_pair("console",     management));
        hintList.push_back(std::make_pair("Management",  management));

        hintList.push_back(std::make_pair("cluster",     ha));
        hintList.push_back(std::make_pair("qpid/ha",     ha));
        hintList.push_back(std::make_pair("qpid\\ha",    ha));
        hintList.push_back(std::make_pair("replication", ha));
        hintList.push_back(std::make_pair("ClusterSafe", ha));

        hintList.push_back(std::make_pair("broker",      broker));
        hintList.push_back(std::make_pair("SessionState",broker));
        hintList.push_back(std::make_pair("DataDir",     broker));
        hintList.push_back(std::make_pair("qpidd",       broker));
        hintList.push_back(std::make_pair("xml",         broker));
        hintList.push_back(std::make_pair("QpidBroker",  broker));

        hintList.push_back(std::make_pair("store",       store));

        hintList.push_back(std::make_pair("assert",      system));
        hintList.push_back(std::make_pair("Exception",   system));
        hintList.push_back(std::make_pair("sys",         system));
        hintList.push_back(std::make_pair("SCM",         system));

        hintList.push_back(std::make_pair("tests",       test));

        hintList.push_back(std::make_pair("messaging",   messaging));
        hintList.push_back(std::make_pair("types",       messaging));

        hintList.push_back(std::make_pair("client",      client));
    }

    static Category categoryOf(const char*const fName);

private:
    std::list<std::pair<const char* const, Category> > hintList;
};

static CategoryFileNameHints filenameHints;

Category CategoryFileNameHints::categoryOf(const char* const fName) {
    for (std::list<std::pair<const char* const, Category> >::iterator
           it  = filenameHints.hintList.begin();
           it != filenameHints.hintList.end();
         ++it) {
        if (strstr(fName, (const char* const)it->first) != 0) {
            return it->second;
        }
    }
    return unspecified;
}


void Statement::categorize(Statement& s) {
    // given a statement and it's category
    // if the category is Unspecified then try to find a
    // better category based on the path and file name.
    if (s.category == log::unspecified) {
        s.category = CategoryFileNameHints::categoryOf(s.file);
    } else {
        // already has a category so leave it alone
    }
}


void Statement::log(const std::string& message) {
    Logger::instance().log(*this, quote(message));
}


Statement::Initializer::Initializer(Statement& s) : statement(s) {
    // QPID-3891:
    // From the given BOOST_CURRENT_FUNCTION name extract only the
    // namespace-qualified-functionName, skipping return and calling args.
    // Given:
    //   <possible return value type> qpid::name::space::Function(args)
    // Return:
    //   "qpid::name::space::Function".
    if (s.function) {
        const char* end = s.function + strlen(s.function);
        const char* fEnd = std::find(s.function, end, '(');
        typedef std::reverse_iterator<const char*> Reverse;
        const char* fBegin = find(Reverse(fEnd), Reverse(s.function), ' ').base();
        size_t n = fEnd - fBegin;
        char* name = new char[n+1];
        std::copy(fBegin, fEnd, name);
        name[n] = '\0';
        s.function = name;
    }
    Statement::categorize(s);
    Logger::instance().add(s);
}

Statement::Initializer::~Initializer() {
    delete[] const_cast<char*>(statement.function);
    statement.function = 0;
}


namespace {
const char* names[LevelTraits::COUNT] = {
    "trace", "debug", "info", "notice", "warning", "error", "critical"
};

const char* catNames[CategoryTraits::COUNT] = {
    "Security", "Broker", "Management", "Protocol", "System", "HA", "Messaging",
    "Store", "Network", "Test", "Client", "Application", "Model", "Unspecified"
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
