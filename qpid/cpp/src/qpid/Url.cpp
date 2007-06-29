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

#include "Url.h"
#include <sstream>
#include <boost/spirit.hpp>
#include <boost/spirit/actor.hpp>

using namespace boost::spirit;
using namespace std;

namespace qpid {

string Url::str() const {
    ostringstream os;
    os << *this;
    return os.str();
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
    if (!boost::spirit::parse(url, UrlGrammar(*this)).full)
        throw InvalidUrl(string("Invalid AMQP url: ")+url);
}

void Url::parseNoThrow(const char* url) {
    if (!boost::spirit::parse(url, UrlGrammar(*this)).full)
        clear();
}

} // namespace qpid
