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

#include "FutureFactory.h"

using namespace qpid::client;
using namespace boost;

shared_ptr<FutureCompletion> FutureFactory::createCompletion()
{
    shared_ptr<FutureCompletion> f(new FutureCompletion());
    weak_ptr<FutureCompletion> w(f);
    set.push_back(w);
    return f;
}

shared_ptr<FutureResponse> FutureFactory::createResponse()
{
    shared_ptr<FutureResponse> f(new FutureResponse());
    weak_ptr<FutureCompletion> w(static_pointer_cast<FutureCompletion>(f));
    set.push_back(w);
    return f;
}

void FutureFactory::close(uint16_t code, const std::string& text)
{
    for (WeakPtrSet::iterator i = set.begin(); i != set.end(); i++) {
        shared_ptr<FutureCompletion> p = i->lock();
        if (p) {
            p->close(code, text);
        }
    }
}
