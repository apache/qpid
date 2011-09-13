#ifndef __QMF_EVENT_NOTIFIER_IMPL_H
#define __QMF_EVENT_NOTIFIER_IMPL_H

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

#include "qmf/AgentSession.h"
#include "qmf/ConsoleSession.h"

namespace qmf
{
    class EventNotifierImpl  {
    private:
        bool readable;
        AgentSession agent;
        ConsoleSession console;

    public:
        EventNotifierImpl(AgentSession& agentSession);
        EventNotifierImpl(ConsoleSession& consoleSession);
        virtual ~EventNotifierImpl();

        void setReadable(bool readable);
        bool isReadable() const;

    protected:
        virtual void update(bool readable) = 0;
    };
}

#endif

