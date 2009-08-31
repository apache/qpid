#ifndef _QmfSequenceManager_
#define _QmfSequenceManager_

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

#include "qpid/sys/Mutex.h"
#include <map>

namespace qmf {

    class SequenceContext {
    public:
        SequenceContext() {}
        virtual ~SequenceContext() {}

        virtual void complete() = 0;
    };

    class SequenceManager {
    public:
        SequenceManager();

        uint32_t reserve(SequenceContext* ctx);
        void release(uint32_t sequence);

    private:
        mutable qpid::sys::Mutex lock;
        uint32_t nextSequence;
        std::map<uint32_t, SequenceContext*> contextMap;
    };

}

#endif

