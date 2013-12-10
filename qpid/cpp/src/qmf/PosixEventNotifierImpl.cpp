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

#include "PosixEventNotifierImpl.h"
#include "qpid/log/Statement.h"

#include <fcntl.h>
#include <unistd.h>
#include <errno.h>

#define BUFFER_SIZE 10

using namespace qmf;

PosixEventNotifierImpl::PosixEventNotifierImpl(AgentSession& agentSession)
    : EventNotifierImpl(agentSession)
{
    openHandle();
}


PosixEventNotifierImpl::PosixEventNotifierImpl(ConsoleSession& consoleSession)
    : EventNotifierImpl(consoleSession)
{
    openHandle();
}


PosixEventNotifierImpl::~PosixEventNotifierImpl()
{
    closeHandle();
}


void PosixEventNotifierImpl::update(bool readable)
{
    char buffer[BUFFER_SIZE];

    if(readable && !this->isReadable()) {
        if (::write(myHandle, "1", 1) == -1)
            QPID_LOG(error, "PosixEventNotifierImpl::update write failed: " << errno);
    }
    else if(!readable && this->isReadable()) {
        if (::read(yourHandle, buffer, BUFFER_SIZE) == -1)
            QPID_LOG(error, "PosixEventNotifierImpl::update read failed: " << errno);
    }
}


void PosixEventNotifierImpl::openHandle()
{
    int pair[2];

    if(::pipe(pair) == -1)
        throw QmfException("Unable to open event notifier handle.");

    yourHandle = pair[0];
    myHandle = pair[1];

    int flags;

    flags = ::fcntl(yourHandle, F_GETFL);
    if((::fcntl(yourHandle, F_SETFL, flags | O_NONBLOCK)) == -1)
        throw QmfException("Unable to make remote handle non-blocking.");

    flags = ::fcntl(myHandle, F_GETFL);
    if((::fcntl(myHandle, F_SETFL, flags | O_NONBLOCK)) == -1)
        throw QmfException("Unable to make local handle non-blocking.");
}


void PosixEventNotifierImpl::closeHandle()
{
    if(myHandle > 0) {
        ::close(myHandle);
        myHandle = -1;
    }

    if(yourHandle > 0) {
        ::close(yourHandle);
        yourHandle = -1;
    }
}


PosixEventNotifierImpl& PosixEventNotifierImplAccess::get(posix::EventNotifier& notifier)
{
    return *notifier.impl;
}


const PosixEventNotifierImpl& PosixEventNotifierImplAccess::get(const posix::EventNotifier& notifier)
{
    return *notifier.impl;
}

