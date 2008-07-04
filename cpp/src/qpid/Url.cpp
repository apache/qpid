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

#include <limits.h>             // NB: must be before boost/spirit headers.
#include <boost/spirit.hpp>
#include <boost/spirit/actor.hpp>

#include <sstream>

#include <sys/ioctl.h>
#include <net/if.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <stdio.h>
#include <errno.h>

using namespace boost::spirit;
using namespace std;

namespace qpid {

std::ostream& operator<<(std::ostream& os, const TcpAddress& a) {
    return os << "tcp:" << a.host << ":" << a.port;
}

std::istream& operator>>(std::istream&, const TcpAddress&);

Url Url::getHostNameUrl(uint16_t port) {
    char name[HOST_NAME_MAX];
    if (::gethostname(name, sizeof(name)) != 0)
        throw InvalidUrl(QPID_MSG("Cannot get host name: " << strError(errno)));
    return Url(TcpAddress(name, port));
}

static const string LOCALHOST("127.0.0.1");

Url Url::getIpAddressesUrl(uint16_t port) {
    Url url;
    int s = socket (PF_INET, SOCK_STREAM, 0);
    for (int i=1;;i++) {
        struct ifreq ifr;
        ifr.ifr_ifindex = i;
        if (::ioctl (s, SIOCGIFNAME, &ifr) < 0)
            break;
        /* now ifr.ifr_name is set */
        if (::ioctl (s, SIOCGIFADDR, &ifr) < 0)
            continue;
        struct sockaddr_in *sin = (struct sockaddr_in *) &ifr.ifr_addr;
        string addr(inet_ntoa(sin->sin_addr));
        if (addr != LOCALHOST)
            url.push_back(TcpAddress(addr, port));
    }
    close (s);
    return url;
}

string Url::str() const {
    if (cache.empty() && !this->empty()) {
        ostringstream os;
        os << *this;
        cache = os.str();
    }
    return cache;
}

ostream& operator<<(ostream& os, const Url& url) {
    Url::const_iterator i = url.begin();
    os << "amqp:";
    if (i!=url.end()) {
        os << *i++;
        while (i != url.end()) 
            os << "," << *i++;
    }
    return os;
}

// Addition to boost::spirit parsers: accept any character from a
// string. Vastly more compile-time-efficient than long rules of the
// form: ch_p('x') | ch_p('y') |...
// 
struct ch_in : public char_parser<ch_in> {
    ch_in(const string& chars_) : chars(chars_) {}
    bool test(char ch_) const {
        return chars.find(ch_) != string::npos;
    }
    string chars;
};

inline ch_in ch_in_p(const string& chars) {
    return ch_in(chars);
}

/** Grammar for AMQP URLs. */
struct UrlGrammar : public grammar<UrlGrammar>
{
    Url& addr;
    
    UrlGrammar(Url& addr_) : addr(addr_) {}

    template <class ScannerT>
    struct definition {
        TcpAddress tcp;

        definition(const UrlGrammar& self)
        {
            first = eps_p[clear_a(self.addr)] >> amqp_url;
            amqp_url = str_p("amqp:") >> prot_addr_list >>
                !(str_p("/") >> !parameters);
            prot_addr_list = prot_addr % ',';            
            prot_addr      = tcp_prot_addr; // Extend for TLS etc.

            // TCP addresses
            tcp_prot_addr  = tcp_id >> tcp_addr[push_back_a(self.addr, tcp)];
            tcp_id         = !str_p("tcp:"); 
            tcp_addr       = !(host[assign_a(tcp.host)] >> !(':' >> port));
            
            // See http://www.apps.ietf.org/rfc/rfc3986.html#sec-A
            // for real host grammar. Shortcut:
            port           = uint_parser<uint16_t>()[assign_a(tcp.port)];
            host           = *( unreserved | pct_encoded );
            unreserved    = alnum_p | ch_in_p("-._~");
            pct_encoded   = "%" >> xdigit_p >> xdigit_p;
            parameters = *anychar_p >> end_p; // Ignore, not used yet.
        }

        const rule<ScannerT>& start() const { return first; }

        rule<ScannerT> first, amqp_url, prot_addr_list, prot_addr,
            tcp_prot_addr, tcp_id, tcp_addr, host, port,
            unreserved, pct_encoded, parameters;
    };
};

void Url::parse(const char* url) {
    cache.clear();
    if (!boost::spirit::parse(url, UrlGrammar(*this)).full)
        throw InvalidUrl(string("Invalid AMQP url: ")+url);
}

void Url::parseNoThrow(const char* url) {
    cache.clear();
    if (!boost::spirit::parse(url, UrlGrammar(*this)).full)
        clear();
}

void Url::throwIfEmpty() const {
    if (empty())
        throw InvalidUrl("URL contains no addresses");
}

std::istream& operator>>(std::istream& is, Url& url) {
    std::string s;
    is >> s;
    url.parse(s);
    return is;
}

} // namespace qpid
