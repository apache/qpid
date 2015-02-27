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

#include "qpid/Url.h"
#include "qpid/Exception.h"
#include "qpid/Msg.h"
#include "qpid/sys/SystemInfo.h"
#include "qpid/sys/StrError.h"
#include "qpid/client/Connector.h"
#include "qpid/sys/Mutex.h"
#include <boost/lexical_cast.hpp>

#include <algorithm>
#include <vector>
#include <string>

#include <string.h>

using namespace std;
using boost::lexical_cast;

namespace qpid {

class ProtocolTags {
  public:
    bool find(const string& tag) {
        sys::Mutex::ScopedLock l(lock);
        return std::find(tags.begin(), tags.end(), tag) != tags.end();
    }

    void add(const string& tag) {
        sys::Mutex::ScopedLock l(lock);
        if (std::find(tags.begin(), tags.end(), tag) == tags.end())
            tags.push_back(tag);
    }

    static ProtocolTags& instance() {
        /** First call must be made while program is still single threaded.
         * This will be the case since tags are registered in static initializers.
         */
        static ProtocolTags tags;
        return tags;
    }
    
  private:
    sys::Mutex lock;
    vector<string> tags;
};

Url::Invalid::Invalid(const string& s) : Exception(s) {}

string Url::str() const {
    if (cache.empty() && !this->empty()) {
        ostringstream os;
        os << *this;
        cache = os.str();
    }
    return cache;
}

ostream& operator<<(ostream& os, const Url& url) {
    os << "amqp:";
    if (!url.getUser().empty()) os << url.getUser();
    if (!url.getPass().empty()) os << "/" << url.getPass();
    if (!(url.getUser().empty() && url.getPass().empty())) os << "@";
    Url::const_iterator i = url.begin();
    if (i!=url.end()) {
        os << *i++;
        while (i != url.end()) 
            os << "," << *i++;
    }
    return os;
}

static const std::string TCP = "tcp";
    
/** Simple recursive-descent parser for this grammar:
url = ["amqp:"][ user ["/" password] "@" ] protocol_addr *("," protocol_addr)
protocol_addr = tcp_addr / rmda_addr / ssl_addr / .. others plug-in
tcp_addr = ["tcp:"] host [":" port]
rdma_addr = "rdma:" host [":" port]
ssl_addr = "ssl:" host [":" port]
*/
class UrlParser {
  public:
    UrlParser(Url& u, const char* s, const std::string& defaultProtocol_=Address::TCP) : url(u), text(s), end(s+strlen(s)), i(s),
                                                                 defaultProtocol(defaultProtocol_) {}
    bool parse() {
        literal("amqp:"); // Optional
        userPass();       // Optional
        return list(&UrlParser::protocolAddr, &UrlParser::comma) && i == end;
    }

  private:
    typedef bool (UrlParser::*Rule)();

    bool userPass() {
        const char* at = std::find(i, end, '@');
        if (at == end) return false;
        const char* slash = std::find(i, at, '/');
        const char* colon = std::find(i, at, ':');
        const char* sep = std::min(slash, colon);
        url.setUser(string(i, sep));
        const char* pass = (sep == at) ? sep : sep+1;
        url.setPass(string(pass, at));
        i = at+1;
        return true;
    }

    bool comma() { return literal(","); }

    bool protocolAddr() {
        Address addr(defaultProtocol, "", Address::AMQP_PORT); // Set up defaults
        protocolTag(addr.protocol);  // Optional
        bool ok = (host(addr.host) &&
                   (literal(":") ? port(addr.port) : true));
        if (ok) url.push_back(addr);
        return ok;
    }

    bool protocolTag(string& result) {
        const char* j = std::find(i,end,':');
        if (j != end) {
            string tag(i,j);
            if (ProtocolTags::instance().find(tag)) {
                i = j+1;
                result = tag;
                return true;
            }
        }
        return false;
    }

    // A liberal interpretation of http://www.ietf.org/rfc/rfc3986.txt.
    // Works for DNS names and and ipv4 and ipv6 literals
    // 
    bool host(string& h) {
        if (ip6literal(h)) return true;

        const char* start=i;
        while (unreserved() || pctEncoded())
            ;
        if (start == i) return false;//host is required
        else h.assign(start, i);
        return true;
    }

    // This is a bit too liberal for IPv6 literal addresses, but probably good enough
    bool ip6literal(string& h) {
        if (literal("[")) {
            const char* start = i;
            while (hexDigit() || literal(":") || literal("."))
                ;
            const char* end = i;
            if ( end-start < 2 ) return false; // Smallest valid address is "::"
            if (literal("]")) {
                h.assign(start, end);
                return true;
            }
        }
        return false;
    }

    bool unreserved() { return (::isalnum(*i) || ::strchr("-._~", *i)) && advance(); }

    bool pctEncoded() { return literal("%") && hexDigit() && hexDigit(); }

    bool hexDigit() { return i < end && ::strchr("01234567890abcdefABCDEF", *i) && advance(); }
    
    bool port(uint16_t& p) { return decimalInt(p); }

    template <class IntType> bool decimalInt(IntType& n) {
        const char* start = i;
        while (decDigit())
            ;
        try {
            n = lexical_cast<IntType>(string(start, i)); 
            return true;
        } catch(...) { return false; }
    }

    bool decDigit() { return i < end && ::isdigit(*i) && advance(); }

    bool literal(const char* s) {
        int n = ::strlen(s);
        if (n <= end-i && equal(s, s+n, i)) return advance(n);
        return false;
    };

    bool noop() { return true; }

    /** List of item, separated by separator, with min & max bounds. */
    bool list(Rule item, Rule separator, size_t min=0, size_t max=UNLIMITED) {
        assert(max > 0);
        assert(max >= min);
        if (!(this->*item)()) return min == 0; // Empty list.
        size_t n = 1;
        while (n < max && i < end) {
            if (!(this->*separator)()) break;
            if (i == end || !(this->*item)()) return false; // Separator with no item.
            ++n;
        }
        return n >= min;
    }

    /** List of items with no separator */
    bool list(Rule item, size_t min=0, size_t max=UNLIMITED) { return list(item, &UrlParser::noop, min, max); }

    bool advance(size_t n=1) {
        if (i+n > end) return false;
        i += n;
        return true;
    }

    static const size_t UNLIMITED = size_t(~1);
    static const std::string LOCALHOST;

    Url& url;
    const char* text;
    const char* end;
    const char* i;
    const std::string defaultProtocol;
};

const string UrlParser::LOCALHOST("127.0.0.1");

void Url::parse(const char* url) {
    parse(url, Address::TCP);
}
void Url::parse(const char* url, const std::string& defaultProtocol) {
    parseNoThrow(url, defaultProtocol);
    if (empty())
        throw Url::Invalid(QPID_MSG("Invalid URL: " << url));
}

void Url::parseNoThrow(const char* url) {
    parseNoThrow(url, Address::TCP);
}

void Url::parseNoThrow(const char* url, const std::string& defaultProtocol) {
    clear();
    cache.clear();
    if (!UrlParser(*this, url, defaultProtocol).parse())
        clear();
}

void Url::throwIfEmpty() const {
    if (empty())
        throw Url::Invalid("URL contains no addresses");
}

std::string Url::getUser() const { return user; }
std::string Url::getPass() const { return pass; }
void Url::setUser(const std::string& s) { user = s; }
void Url::setPass(const std::string& s) { pass = s; }

std::istream& operator>>(std::istream& is, Url& url) {
    std::string s;
    is >> s;
    url.parse(s);
    return is;
}

void Url::addProtocol(const std::string& tag) { ProtocolTags::instance().add(tag); }

} // namespace qpid
