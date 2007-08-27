/*
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

#include "SessionAdapter.h"

namespace qpid {
namespace broker {

SessionAdapter::SessionAdapter() {
    // FIXME aconway 2007-08-27: Implement
}

void  SessionAdapter::visit(const SessionOpenBody&) {
    // FIXME aconway 2007-08-27: Implement
}

void  SessionAdapter::visit(const SessionAckBody&) {
    // FIXME aconway 2007-08-27: Implement
}


void  SessionAdapter::visit(const SessionAttachedBody&) {
    // FIXME aconway 2007-08-27: Implement
}


void  SessionAdapter::visit(const SessionCloseBody&) {
    // FIXME aconway 2007-08-27: Implement
}


void  SessionAdapter::visit(const SessionClosedBody&) {
    // FIXME aconway 2007-08-27: Implement
}


void  SessionAdapter::visit(const SessionDetachedBody&) {
    // FIXME aconway 2007-08-27: Implement
}


void  SessionAdapter::visit(const SessionFlowBody&) {
    // FIXME aconway 2007-08-27: Implement
}


void  SessionAdapter::visit(const SessionFlowOkBody&) {
    // FIXME aconway 2007-08-27: Implement
}


void  SessionAdapter::visit(const SessionHighWaterMarkBody&) {
    // FIXME aconway 2007-08-27: Implement
}


void  SessionAdapter::visit(const SessionResumeBody&) {
    // FIXME aconway 2007-08-27: Implement
}


void  SessionAdapter::visit(const SessionSolicitAckBody&) {
    // FIXME aconway 2007-08-27: Implement
}


void  SessionAdapter::visit(const SessionSuspendBody&) {
    // FIXME aconway 2007-08-27: Implement
}

}} // namespace qpid::broker
