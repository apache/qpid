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
 * \file AsyncStoreOptions.h
 */

#ifndef qpid_asyncStore_AsyncStoreOptions_h_
#define qpid_asyncStore_AsyncStoreOptions_h_

#include "qpid/asyncStore/jrnl2/Streamable.h"

#include "qpid/Options.h"

#include <string>

namespace qpid {
namespace broker {
class Options;
}
namespace asyncStore {

class AsyncStoreOptions : public qpid::Options
{
public:
    AsyncStoreOptions(const std::string& name="Async Store Options");
    AsyncStoreOptions(const std::string& storeDir,
                      const std::string& name="Async Store Options");
    virtual ~AsyncStoreOptions();
    void printVals(std::ostream& os) const;
    void validate();

    std::string m_storeDir;

private:
    // Static initialization race condition avoidance with static instance of Plugin class (using construct-on-first-use idiom).
    static std::string& getDefaultStoreDir();
};

}} // namespace qpid::asyncStore

#endif // qpid_asyncStore_AsyncStoreOptions_h_
