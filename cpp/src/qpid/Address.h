#ifndef QPID_ADDRESS_H
#define QPID_ADDRESS_H

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

#include "qpid/sys/IntegerTypes.h"

#include <boost/variant.hpp>
#include <ostream>
#include <string>
#include <vector>

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

/**
 * Address is a variant of all address types, more coming in future.
 *
 * Address wraps a boost::variant rather than be defined in terms of
 * boost::variant to prevent users from having to deal directly with
 * boost.
 */
struct Address  {
public:
    Address(const Address& a) : value(a.value) {}
    Address(const TcpAddress& tcp) : value(tcp) {}
    template <class T> Address& operator=(const T& t) { value=t; return *this; }
    template <class T> T* get() { return boost::get<T>(&value); }
    template <class T> const T* get() const { return boost::get<T>(&value); }

private:
    boost::variant<TcpAddress> value;
};

std::ostream& operator<<(std::ostream& os, const Address& addr);

} // namespace qpid

#endif  /*!QPID_ADDRESS_H*/
