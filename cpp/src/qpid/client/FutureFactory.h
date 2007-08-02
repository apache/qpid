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

#ifndef _FutureFactory_
#define _FutureFactory_

#include <vector>
#include <boost/shared_ptr.hpp>
#include <boost/weak_ptr.hpp>
#include "FutureCompletion.h"
#include "FutureResponse.h"

namespace qpid {
namespace client {

class FutureFactory 
{
    typedef std::vector< boost::weak_ptr<FutureCompletion> > WeakPtrSet;
    WeakPtrSet set;

public:
    boost::shared_ptr<FutureCompletion> createCompletion();
    boost::shared_ptr<FutureResponse> createResponse();
    void close(uint16_t code, const std::string& text);
};

}}


#endif
