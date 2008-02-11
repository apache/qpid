#ifndef QPID_URL_H
#define QPID_URL_H

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

#include "qpid/Exception.h"
#include <boost/variant.hpp>
#include <string>
#include <vector>
#include <new>
#include <ostream>

namespace qpid {

/** TCP address of a broker - host:port */
struct TcpAddress {
    static const uint16_t DEFAULT_PORT=5672;
    explicit TcpAddress(const std::string& host_=std::string(),
               uint16_t port_=DEFAULT_PORT)
        : host(host_), port(port_) {}
    std::string host;
    uint16_t port;
};

inline bool operator==(const TcpAddress& x, const TcpAddress& y) {
    return y.host==x.host && y.port == x.port;
}

std::ostream& operator<<(std::ostream& os, const TcpAddress& a);

/** Address is a variant of all address types, more coming in future. */
typedef boost::variant<TcpAddress> Address;

/** An AMQP URL contains a list of addresses */
struct Url : public std::vector<Address> {

    /** Url with the hostname as returned by gethostname(2)  */
    static Url getHostNameUrl(uint16_t port);

    /** Url with local IP address(es), may be more than one address
     * on a multi-homed host. */
    static Url getIpAddressesUrl(uint16_t port);

    struct InvalidUrl : public Exception {
        InvalidUrl(const std::string& s) : Exception(s) {}
    };

    /** Convert to string form. */
    std::string str() const;

    /** Empty URL. */
    Url() {}
    
    /** URL containing a single address */
    explicit Url(const Address& addr) { push_back(addr); }

    /** Parse url, throw InvalidUrl if invalid. */
    explicit Url(const std::string& url) { parse(url.c_str()); }

    /** Parse url, throw InvalidUrl if invalid. */
    explicit Url(const char* url) { parse(url); }

    template<class T> Url& operator=(T s) { parse(s); return *this; }
    
    /** Replace contents with parsed URL as defined in
     * https://wiki.108.redhat.com/jira/browse/AMQP-95
     *@exception InvalidUrl if the url is invalid.
     */
    void parse(const char* url);
    void parse(const std::string& url) { parse(url.c_str()); }

    /** Replace contesnts with parsed URL as defined in
     * https://wiki.108.redhat.com/jira/browse/AMQP-95
     * url.empty() will be true if url is invalid.
     */
    void parseNoThrow(const char* url);

  private:
    mutable std::string cache;  // cache string form for efficiency.
};

inline bool operator==(const Url& a, const Url& b) { return a.str()==b.str(); }
inline bool operator!=(const Url& a, const Url& b) { return a.str()!=b.str(); }

std::ostream& operator<<(std::ostream& os, const Url& url);
std::istream& operator>>(std::istream& is, Url& url);

} // namespace qpid

#endif  /*!QPID_URL_H*/
