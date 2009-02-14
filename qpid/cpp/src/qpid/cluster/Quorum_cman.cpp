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
#include "Quorum_cman.h"
#include "qpid/log/Statement.h"
#include "qpid/Options.h"
#include "qpid/sys/Time.h"

namespace qpid {
namespace cluster {

Quorum::Quorum() : enable(false), cman(0) {}

Quorum::~Quorum() { if (cman) cman_finish(cman); }

void Quorum::init() {
    QPID_LOG(info, "Waiting for cluster quorum");
    enable = true;
    cman = cman_init(0);
    if (cman == 0) throw ErrnoException("Can't connect to cman service");
    if (!cman_is_quorate(cman)) {
        QPID_LOG(notice, "Waiting for cluster quorum.");
        while(!cman_is_quorate(cman)) sys::sleep(5);
    }
}

bool Quorum::isQuorate() { return enable ? cman_is_quorate(cman) : true; }

}} // namespace qpid::cluster
