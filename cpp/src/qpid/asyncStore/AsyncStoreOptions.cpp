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
 * \file AsyncStoreOptions.cpp
 */

#include "AsyncStoreOptions.h"

namespace qpid {
namespace asyncStore {

AsyncStoreOptions::AsyncStoreOptions(const std::string& name):
        qpid::Options(name),
        m_storeDir(getDefaultStoreDir())
{
    addOptions()
        ("store-dir", qpid::optValue(m_storeDir, "DIR"),
            "Store directory location for persistence (instead of using --data-dir value). "
            "Required if --no-data-dir is also used.")
    ;
}

AsyncStoreOptions::AsyncStoreOptions(const std::string& storeDir,
                                     const std::string& name) :
        qpid::Options(name),
        m_storeDir(storeDir)
{}

AsyncStoreOptions::~AsyncStoreOptions()
{}

void
AsyncStoreOptions::printVals(std::ostream& os) const
{
    os << "ASYNC STORE OPTIONS:" << std::endl;
    os << "      Store directory location for persistence [store-dir]: \"" <<  m_storeDir << "\"" << std::endl;
}

void
AsyncStoreOptions::validate()
{}

//static
std::string&
AsyncStoreOptions::getDefaultStoreDir()
{
    static std::string s_defaultStoreDir = "/tmp";
    return s_defaultStoreDir;
}

}} // namespace qpid::asyncStore
