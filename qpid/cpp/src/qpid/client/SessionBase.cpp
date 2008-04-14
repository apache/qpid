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
#include "SessionBase.h"

namespace qpid {
namespace client {
using namespace framing;

SessionBase::SessionBase() {}
SessionBase::~SessionBase() {}
SessionBase::SessionBase(shared_ptr<SessionCore> core) : impl(core) {}
void SessionBase::suspend() { impl->suspend(); }
void SessionBase::close() { impl->close(); }

void SessionBase::setSynchronous(bool isSync) { impl->setSync(isSync); }
void SessionBase::setSynchronous(SynchronousMode m) { impl->setSync(m); }
bool SessionBase::isSynchronous() const { return impl->isSync(); }
SynchronousMode SessionBase::getSynchronous() const {
    return SynchronousMode(impl->isSync());
}

Execution& SessionBase::getExecution() { return impl->getExecution(); }
Uuid SessionBase::getId() const { return impl->getId(); }
framing::FrameSet::shared_ptr SessionBase::get() { return impl->get(); }

void SessionBase::sync() {
    Execution& ex = getExecution();
    ex.syncWait(ex.lastSent());
    impl->assertOpen();
}

}} // namespace qpid::client
