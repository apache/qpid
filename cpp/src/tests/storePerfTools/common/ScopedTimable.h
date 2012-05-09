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
 * \file ScopedTimable.h
 */

#ifndef tests_storePerfTools_common_ScopedTimable_h_
#define tests_storePerfTools_common_ScopedTimable_h_

namespace tests {
namespace storePerftools {
namespace common {

/**
 * \brief Scoped timer class that starts timing on construction and finishes on destruction.
 *
 * This class is designed to be the parent class for a performance result class which depends on the elapsed
 * time of some process or event. By passing this (or its subclasses) to ScopedTimer (which only exists within
 * the scope of the event), the _elapsed member of this class will be written with the elapsed time when the
 * ScopedTimer object goes out of scope or is destroyed.
 *
 * Subclasses may be aware of the parameters being timed, and may thus print and/or display performance and/or
 * rate information for these parameters.
 */
class ScopedTimable
{
public:
    ScopedTimable();
    virtual ~ScopedTimable();
    double& getElapsedRef();

protected:
    double m_elapsed;   ///< Elapsed time, will be written on destruction of ScopedTimer instances

};

}}} // namespace tests::storePerftools::common

#endif // tests_storePerfTools_common_ScopedTimable_h_
