#ifndef QPID_CLUSTER_EXP_PRETTYID_H
#define QPID_CLUSTER_EXP_PRETTYID_H

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
#include "qpid/cluster/types.h"

namespace qpid {
namespace cluster {

/**
 * Wrapper for a MemberId that prints as the member ID or the string
 * "self" if the member is self.
 */
struct  PrettyId {
    MemberId id, self;
    PrettyId(const MemberId& id_, const MemberId& self_) : id(id_), self(self_) {}
};

inline std::ostream& operator<<(std::ostream& o, const PrettyId& id) {
    if (id.id == id.self) return o << "self";
    else return o << id.id;
}


}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EXP_PRETTYID_H*/
