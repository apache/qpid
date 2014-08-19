#ifndef QPID_HA_ROLE_H
#define QPID_HA_ROLE_H

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

#include <string>

namespace qpid {
struct Url;

namespace ha {

/**
 * A HaBroker has a role, e.g. Primary, Backup, StandAlone.
 * Role subclasses define the actions of the broker in each role.
 * The Role interface allows the HaBroker to pass management actions
 * to be implemented by the role.
 */
class Role
{
  public:
    virtual ~Role() {}

    /** QMF promote method handler.
     * @return The new role if promoted, 0 if not. Caller takes ownership.
     */
    virtual Role* promote() = 0;

    virtual void setBrokerUrl(const Url& url) = 0;

  private:
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_ROLE_H*/
