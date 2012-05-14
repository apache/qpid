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

/**
 * \file TestResult.h
 */

#ifndef tests_storePerftools_common_TestResult_h_
#define tests_storePerftools_common_TestResult_h_

#include "ScopedTimable.h"
#include "Streamable.h"

namespace tests {
namespace storePerftools {
namespace common {

class TestResult : public ScopedTimable, public Streamable
{
public:
    TestResult();
    virtual ~TestResult();
    void toStream(std::ostream& os = std::cout) const = 0;
};

}}} // namespace tests:storePerftools::common

#endif // tests_storePerftools_common_TestResult_h_
