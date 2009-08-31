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

#include "qmf/SequenceManager.h"

using namespace std;
using namespace qmf;
using namespace qpid::sys;

SequenceManager::SequenceManager() : nextSequence(1) {}

uint32_t SequenceManager::reserve(SequenceContext* ctx)
{
    Mutex::ScopedLock _lock(lock);
    uint32_t seq = nextSequence;
    while (contextMap.find(seq) != contextMap.end())
        seq = seq < 0xFFFFFFFF ? seq + 1 : 1;
    nextSequence = seq < 0xFFFFFFFF ? seq + 1 : 1;
    contextMap[seq] = ctx;
    return seq;
}

void SequenceManager::release(uint32_t sequence)
{
    Mutex::ScopedLock _lock(lock);
    map<uint32_t, SequenceContext*>::iterator iter = contextMap.find(sequence);
    if (iter != contextMap.end()) {
        if (iter->second != 0)
            iter->second->complete();
        contextMap.erase(iter);
    }
}


