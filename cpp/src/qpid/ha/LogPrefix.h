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

#include "Enum.h"
#include <iosfwd>
#include <string>

namespace qpid {
namespace ha {

class HaBroker;

/**
 * Standard information to prefix log messages.
 */
class LogPrefix
{
  public:
    /** For use by all classes other than HaBroker */
    LogPrefix(HaBroker& hb, const std::string& queue=std::string());
    LogPrefix(LogPrefix& lp, const std::string& queue);
    /** For use by the HaBroker itself. */
    LogPrefix(BrokerStatus&);

    void setMessage(const std::string&);

  private:
    HaBroker* haBroker;
    BrokerStatus* status;
    std::string tail;
  friend std::ostream& operator<<(std::ostream& o, const LogPrefix& l);
};

std::ostream& operator<<(std::ostream& o, const LogPrefix& l);

}} // namespace qpid::ha

#endif  /*!QPID_HA_LOGPREFIX_H*/
