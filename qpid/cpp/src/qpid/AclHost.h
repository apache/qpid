#ifndef QPID_ACLHOST_H
#define QPID_ACLHOST_H

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

#include "qpid/sys/SocketAddress.h"
#include "qpid/Exception.h"
#include <utility>
#include <string>
#include <vector>
#include <new>
#include <ostream>
#include <boost/shared_ptr.hpp>
#include "qpid/CommonImportExport.h"

namespace qpid {

/** An AclHost contains shared_ptrs to two SocketAddresses
 * representing a low-high pair of addresses.
 */
class AclHost {
  public:
    typedef boost::shared_ptr<sys::SocketAddress> SAptr;

    struct Invalid : public Exception { QPID_COMMON_EXTERN Invalid(const std::string& s); };

    /** Convert to string form. */
    QPID_COMMON_EXTERN std::string str() const;

    /** Empty AclHost. */
    AclHost() : allAddresses(false) {}

    explicit AclHost(const std::string& hostSpec) : allAddresses(false) { parse(hostSpec); }

    QPID_COMMON_EXTERN std::string comparisonDetails() const {
        if (loSAptr.get()) {
            return loSAptr->comparisonDetails(*hiSAptr);
        } else {
            return "(all)";
        }
    }

    QPID_COMMON_EXTERN bool match(const std::string& hostIp) const;

    QPID_COMMON_EXTERN bool match(const sys::SocketAddress& sa) const;

    QPID_COMMON_EXTERN void parse(       const std::string& hostSpec);
    QPID_COMMON_EXTERN void parseNoThrow(const std::string& hostSpec);

    QPID_COMMON_EXTERN void clear() {
        cache.clear();
        loSAptr.reset();
        hiSAptr.reset();
    }

    QPID_COMMON_EXTERN bool isEmpty() const {
        return !loSAptr.get() && !hiSAptr.get(); }

    QPID_COMMON_EXTERN bool isAllAddresses() const { return allAddresses; }

  private:
    mutable std::string cache;  // cache string form for efficiency.

    bool allAddresses;
    SAptr loSAptr;
    SAptr hiSAptr;

  friend class AclHostParser;
};

inline bool operator==(const AclHost& a, const AclHost& b) { return a.str()==b.str(); }
inline bool operator!=(const AclHost& a, const AclHost& b) { return a.str()!=b.str(); }

QPID_COMMON_EXTERN std::ostream& operator<<(std::ostream& os, const AclHost& aclhost);
QPID_COMMON_EXTERN std::istream& operator>>(std::istream& is, AclHost& aclhost);

} // namespace qpid

#endif  /*!QPID_ACLHOST_H*/
