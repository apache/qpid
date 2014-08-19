#ifndef QPID_HA_LOGPREFIX_H
#define QPID_HA_LOGPREFIX_H

/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#include <qpid/sys/Mutex.h>
#include <string>
#include <iosfwd>

namespace qpid {
namespace ha {

/**
 * Thread safe string holder to hold a string that may be read and modified concurrently.
 */
class LogPrefix
{
  public:
    explicit LogPrefix(const std::string& s=std::string()) : prefix(s) {}
    void set(const std::string& s) { sys::RWlock::ScopedWlock l(lock); prefix = s; }
    std::string get() const { sys::RWlock::ScopedRlock l(lock); return prefix; }

    LogPrefix& operator=(const std::string& s) { set(s); return *this; }
    operator std::string() const { return get(); }

  private:
    // Undefined, not copyable.
    LogPrefix(const LogPrefix& lp);
    LogPrefix& operator=(const LogPrefix&);

    mutable sys::RWlock lock;
    std::string prefix;
};
std::ostream& operator<<(std::ostream& o, const LogPrefix& lp);

/**
 * A two-part log prefix with a reference to a pre-prefix and a post-prefix.
 * Operator << will print both parts, get/set just manage the post-prefix.
 */
class LogPrefix2 : public LogPrefix {
  public:
    const LogPrefix& prePrefix;
    explicit LogPrefix2(const LogPrefix& lp, const std::string& s=std::string()) : LogPrefix(s), prePrefix(lp) {}
    LogPrefix2& operator=(const std::string& s) { set(s); return *this; }

  private:
    // Undefined, not copyable.
    LogPrefix2(const LogPrefix2& lp);
    LogPrefix2& operator=(const LogPrefix2&);
};
std::ostream& operator<<(std::ostream& o, const LogPrefix2& lp);


}} // namespace qpid::ha

#endif  /*!QPID_HA_LOGPREFIX_H*/
