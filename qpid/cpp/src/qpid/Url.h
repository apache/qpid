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
    static const uint16_t defaultPort=5672;
    TcpAddress(const std::string& host_=std::string(),
               uint16_t port_=defaultPort)
        : host(host_), port(port_) {}
    std::string host;
    uint16_t port;
};

bool operator==(const TcpAddress& x, const TcpAddress& y) {
    return y.host==x.host && y.port == x.port;
}

std::ostream& operator<<(std::ostream& os, const TcpAddress& a) {
    return os << "tcp:" << a.host << ":" << a.port;
}

/** Address is a variant of all address types. */
typedef boost::variant<TcpAddress> Address;


/** An AMQP URL contains a list of addresses */
struct Url : public std::vector<Address> {
    struct InvalidUrl : public Exception {
        InvalidUrl(const std::string& s) : Exception(s) {}
    };

    /** Convert to string form. */
    std::string str() const;

    /** Empty URL. */
    Url() {}
    
    /** Parse an amqp URL string as defined in
     * https://wiki.108.redhat.com/jira/browse/AMQP-95
     *@exception InvalidUrl if the url is invalid.
     */
    Url(const std::string& url);

    /** Parse an amqp URL string as defined in
     * https://wiki.108.redhat.com/jira/browse/AMQP-95
     * Results in empty URL if url is invalid.
     */
    Url(const std::string& url, const std::nothrow_t&);
};

std::ostream& operator<<(std::ostream& os, const Url& url);

} // namespace qpid

#endif  /*!QPID_URL_H*/
