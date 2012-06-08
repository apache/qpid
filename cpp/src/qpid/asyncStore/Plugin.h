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
 * \file Plugin.h
 */

#ifndef qpid_broker_Plugin_h_
#define qpid_broker_Plugin_h_

#include "AsyncStoreImpl.h"
#include "AsyncStoreOptions.h"

#include "qpid/Plugin.h"

namespace qpid {
class Options;
namespace broker {

class Plugin : public qpid::Plugin
{
public:
    virtual void earlyInitialize(Target& target);
    virtual void initialize(Target& target);
    void finalize();
    virtual qpid::Options* getOptions();
private:
    boost::shared_ptr<qpid::asyncStore::AsyncStoreImpl> m_store;
    qpid::asyncStore::AsyncStoreOptions m_options;
};

static Plugin instance; // Static initialization.

}} // namespace qpid::broker

#endif // qpid_broker_Plugin_h_
