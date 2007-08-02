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

#include "FutureCompletion.h"

using namespace qpid::client;
using namespace qpid::sys;

FutureCompletion::FutureCompletion() : complete(false), closed(false), code(0) {}

bool FutureCompletion::isComplete()
{
    Monitor::ScopedLock l(lock);
    return complete;
}

void FutureCompletion::completed()
{
    Monitor::ScopedLock l(lock);
    complete = true;
    lock.notifyAll();
}

void FutureCompletion::waitForCompletion()
{
    Monitor::ScopedLock l(lock);
    while (!complete && !closed) {
        lock.wait();
    }
    if (closed) {
        throw ChannelException(code, text);
    }
}

void FutureCompletion::close(uint16_t _code, const std::string& _text)
{
    Monitor::ScopedLock l(lock);
    complete = true;
    closed = true;
    code = _code;
    text = _text;        
    lock.notifyAll();
}
