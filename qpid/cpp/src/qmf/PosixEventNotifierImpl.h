#ifndef __QMF_POSIX_EVENT_NOTIFIER_IMPL_H
#define __QMF_POSIX_EVENT_NOTIFIER_IMPL_H

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
#include "qmf/EventNotifierImpl.h"
#include "qpid/RefCounted.h"

namespace qmf
{
    class AgentSession;
    class ConsoleSession;

    class PosixEventNotifierImpl : public EventNotifierImpl, public virtual qpid::RefCounted
    {
    public:
        PosixEventNotifierImpl(AgentSession& agentSession);
        PosixEventNotifierImpl(ConsoleSession& consoleSession);
        virtual ~PosixEventNotifierImpl();

        int getHandle() const { return yourHandle; }

    private:
        int myHandle;
        int yourHandle;

        void openHandle();
        void closeHandle();

    protected:
        void update(bool readable);
    };

    struct PosixEventNotifierImplAccess
    {
        static PosixEventNotifierImpl& get(posix::EventNotifier& notifier);
        static const PosixEventNotifierImpl& get(const posix::EventNotifier& notifier);
    };

}

#endif

