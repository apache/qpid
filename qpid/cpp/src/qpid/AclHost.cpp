/*
 *
 * Copyright (c) 2014 The Apache Software Foundation
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

#include "qpid/AclHost.h"
#include "qpid/Exception.h"
#include "qpid/Msg.h"
#include "qpid/StringUtils.h"
#include "qpid/log/Logger.h"
#include "qpid/sys/SocketAddress.h"

#include <vector>
#include <string>

using namespace std;

namespace qpid {

AclHost::Invalid::Invalid(const string& s) : Exception(s) {}

string AclHost::str() const {
    if (cache.empty()) {
        ostringstream os;
        os << *this;
        cache = os.str();
    }
    return cache;
}

std::string undecorateIPv6Name(std::string& host) {
    std::string s(host);
    if (host.length() >= 3 && host.find("[") == 0 && host.rfind("]") == host.length()-1)
        s = host.substr(1, host.length()-2);
    return s;
}

ostream& operator<<(ostream& os, const AclHost& aclhost) {
    os << aclhost.comparisonDetails();
    return os;
}

class AclHostParser {
  public:
    AclHostParser(AclHost& ah, const std::string& hSpec) :
        aclhost(ah), hostSpec(hSpec) {}

    bool parse() {
        // Convert given host spec into vector of host names
        // Blank host name means "all addresses. Create AclHost
        // with no SocketAddress objects
        if (hostSpec.compare("") == 0) {
            aclhost.allAddresses = true;
            return true;
        }
        std::vector<string> hostList;
        split(hostList, hostSpec, ",");
        if (hostList.size() == 0 || hostList.size() > 2) {
            throw AclHost::Invalid(
                QPID_MSG("Invalid AclHost: hostlist must be one name or "
                         "two names separated with a comma : " << hostSpec));
        }
        // Create pairs of SocketAddress objects representing the host range
        if (hostList.size() == 1) {
            hostList[0] = undecorateIPv6Name(hostList[0]);
            aclhost.loSAptr = AclHost::SAptr(new sys::SocketAddress(hostList[0], ""));
            aclhost.hiSAptr = AclHost::SAptr(new sys::SocketAddress(hostList[0], ""));
        } else {
            hostList[0] = undecorateIPv6Name(hostList[0]);
            hostList[1] = undecorateIPv6Name(hostList[1]);
            aclhost.loSAptr = AclHost::SAptr(new sys::SocketAddress(hostList[0], ""));
            aclhost.hiSAptr = AclHost::SAptr(new sys::SocketAddress(hostList[1], ""));
        }
        // Make sure that this pair will work for run-time comparisons
        if (!aclhost.loSAptr->isComparable(*aclhost.hiSAptr)) {
            throw AclHost::Invalid(
                QPID_MSG("AclHost specifies hosts that cannot be compared : " << hostSpec));
        }

        return true;
    }

    AclHost& aclhost;
    const std::string& hostSpec;
};

void AclHost::parse(const std::string& hostSpec) {
    parseNoThrow(hostSpec);
    if (isEmpty() && !allAddresses)
        throw AclHost::Invalid(QPID_MSG("Invalid AclHost : " << hostSpec));
}

void AclHost::parseNoThrow(const std::string& hostSpec) {
    clear();
    try {
        if (!AclHostParser(*this, hostSpec).parse())
            clear();
    } catch (...) {
        clear();
    }
}

std::istream& operator>>(std::istream& is, AclHost& aclhost) {
    std::string s;
    is >> s;
    aclhost.parse(s);
    return is;
}

/**
 * Given a connecting host's numeric IP address as a string
 * Return true if the host is in the range of any of our kept
 * SocketAddress's binary address ranges.
 */
bool AclHost::match(const std::string& hostIp) const {
    try {
        sys::SocketAddress sa1(hostIp, "");
        return match(sa1);
    } catch (...) {
        return false;
    }
}

/**
 * Given a connecting host's SocketAddress
 * Return true if the host is in the range of any of our kept
 * SocketAddress's binary address ranges.
 */
bool AclHost::match(const sys::SocketAddress& peer) const {
    if (!loSAptr.get()) {
        // No kept socket address means "all addresses"
        return true;
    }
    bool result = peer.inRange(*loSAptr, *hiSAptr);
    return result;
}

} // namespace qpid
