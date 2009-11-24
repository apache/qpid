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
#include "StoreStatus.h"
#include "qpid/Exception.h"
#include <boost/filesystem/path.hpp>
#include <boost/filesystem/fstream.hpp>
#include <boost/filesystem/operations.hpp>
#include <fstream>

namespace qpid {
namespace cluster {

using framing::Uuid;
using namespace framing::cluster;
using namespace boost::filesystem;

StoreStatus::StoreStatus(const std::string& d)
    : state(STORE_STATE_NO_STORE), dataDir(d)
{}

namespace {

const char* SUBDIR="cluster";
const char* START_FILE="start";
const char* STOP_FILE="stop";

Uuid loadUuid(const path& path) {
    Uuid ret;
    if (exists(path)) {
        ifstream i(path);
        i >> ret;
    }
    return ret;
}

void saveUuid(const path& path, const Uuid& uuid) {
    ofstream o(path);
    o << uuid;
}

} // namespace


void StoreStatus::load() {
    path dir = path(dataDir)/SUBDIR;
    create_directory(dir);
    start = loadUuid(dir/START_FILE);
    stop = loadUuid(dir/STOP_FILE);

    if (start && stop) state = STORE_STATE_CLEAN_STORE;
    else if (start) state = STORE_STATE_DIRTY_STORE;
    else state = STORE_STATE_EMPTY_STORE;
}

void StoreStatus::save() {
    path dir = path(dataDir)/SUBDIR;
    create_directory(dir);
    saveUuid(dir/START_FILE, start);
    saveUuid(dir/STOP_FILE, stop);
}

void StoreStatus::dirty(const Uuid& start_) {
    start = start_;
    stop = Uuid();
    state = STORE_STATE_DIRTY_STORE;
    save();
}

void StoreStatus::clean(const Uuid& stop_) {
    assert(start);              // FIXME aconway 2009-11-20: exception?
    assert(stop_);
    state = STORE_STATE_CLEAN_STORE;
    stop = stop_;
    save();
}

}} // namespace qpid::cluster

