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
 */

#include "qmf/posix/EventNotifier.h"
#include "qmf/PosixEventNotifierImpl.h"
#include "qmf/PrivateImplRef.h"

using namespace qmf;
using namespace std;

typedef qmf::PrivateImplRef<posix::EventNotifier> PI;

posix::EventNotifier::EventNotifier(PosixEventNotifierImpl* impl) { PI::ctor(*this, impl); }

posix::EventNotifier::EventNotifier(AgentSession& agentSession)
{
    PI::ctor(*this, new PosixEventNotifierImpl(agentSession));
}


posix::EventNotifier::EventNotifier(ConsoleSession& consoleSession)
{
    PI::ctor(*this, new PosixEventNotifierImpl(consoleSession));
}


posix::EventNotifier::EventNotifier(const posix::EventNotifier& that)
    : Handle<PosixEventNotifierImpl>()
{
    PI::copy(*this, that);
}


posix::EventNotifier::~EventNotifier()
{
    PI::dtor(*this);
}

posix::EventNotifier& posix::EventNotifier::operator=(const posix::EventNotifier& that)
{
    return PI::assign(*this, that);
}


int posix::EventNotifier::getHandle() const
{
    return impl->getHandle();
}

