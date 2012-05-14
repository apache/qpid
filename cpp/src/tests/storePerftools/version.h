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
 * \file version.h
 */

#ifndef tests_storePerftools_version_h_
#define tests_storePerftools_version_h_

#include <iostream>
#include <sstream>

namespace tests {
namespace storePerftools {

static const int versionMajor = 0;
static const int versionMinor = 0;
static const int versionRevision = 1;

std::string name() {
    return "Qpid async store perftools";
}

std::string version() {
    std::ostringstream oss;
    oss << versionMajor << "." << versionMinor << "." << versionRevision;
    return oss.str();
}

}} // namespace tests::perftools

#endif // tests_storePerftools_version_h_
