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
#include "qpid/Msg.h"
#include "qpid/log/Statement.h"
#include <boost/filesystem/path.hpp>
#include <boost/filesystem/fstream.hpp>
#include <boost/filesystem/operations.hpp>
#include <boost/scoped_array.hpp>
#include <fstream>
#include <sstream>

namespace qpid {
namespace cluster {

using framing::Uuid;
using namespace framing::cluster;
namespace fs=boost::filesystem;
using namespace std;

StoreStatus::StoreStatus(const std::string& d)
    : state(STORE_STATE_NO_STORE), dataDir(d)
{}

namespace {

const char* SUBDIR="cluster";
const char* STORE_STATUS="store.status";

string readFile(const fs::path& path) {
    fs::ifstream is;
    is.exceptions(std::ios::badbit | std::ios::failbit);
    is.open(path);
    // get length of file:
    is.seekg (0, ios::end);
    size_t length = is.tellg();
    is.seekg (0, ios::beg);
    // load data
    boost::scoped_array<char> buffer(new char[length]);
    is.read(buffer.get(), length);
    is.close();
    return string(buffer.get(), length);
}

void writeFile(const fs::path& path, const string& data) {
    fs::ofstream os;
    os.exceptions(std::ios::badbit | std::ios::failbit);
    os.open(path);
    os.write(data.data(), data.size());
    os.close();
}

} // namespace


void StoreStatus::load() {
    if (dataDir.empty()) {
        throw Exception(QPID_MSG("No data-dir: When a store is loaded together with clustering, --data-dir must be specified."));
    }
    try {
        fs::path dir = fs::path(dataDir, fs::native)/SUBDIR;
        create_directory(dir);
        fs::path file = dir/STORE_STATUS;
        if (fs::exists(file)) {
            string data = readFile(file);
            istringstream is(data);
            is.exceptions(std::ios::badbit | std::ios::failbit);
            is >> ws >> clusterId >> ws >> shutdownId;
            if (!clusterId)
                throw Exception(QPID_MSG("Invalid cluster store state, no cluster-id"));
            if (shutdownId) state = STORE_STATE_CLEAN_STORE;
            else state = STORE_STATE_DIRTY_STORE;
        }
        else {                  // Starting from empty store
            clusterId = Uuid(true);
            save();
            state = STORE_STATE_EMPTY_STORE;
        }
    }
    catch (const std::exception&e) {
        throw Exception(QPID_MSG("Cannot load cluster store status: " << e.what()));
    }
}

void StoreStatus::save() {
    if (dataDir.empty()) return;
    try {
        ostringstream os;
        os << clusterId << endl << shutdownId << endl;
        fs::path file = fs::path(dataDir, fs::native)/SUBDIR/STORE_STATUS;
        writeFile(file, os.str());
    }
    catch (const std::exception& e) {
        throw Exception(QPID_MSG("Cannot save cluster store status: " << e.what()));
    }
}

bool StoreStatus::hasStore() const {
    return state != framing::cluster::STORE_STATE_NO_STORE;
}

void StoreStatus::dirty() {
    assert(hasStore());
    if (shutdownId) {
        shutdownId = Uuid();
        save();
    }
    state = STORE_STATE_DIRTY_STORE;
}

void StoreStatus::clean(const Uuid& shutdownId_) {
    assert(hasStore());
    assert(shutdownId_);
    if (shutdownId_ != shutdownId) {
        shutdownId = shutdownId_;
        save();
    }
    state = STORE_STATE_CLEAN_STORE;
}

void StoreStatus::setClusterId(const Uuid& clusterId_) {
    clusterId = clusterId_;
    save();
}

const char* stateName(StoreState s) {
    switch (s) {
      case STORE_STATE_NO_STORE: return "none";
      case STORE_STATE_EMPTY_STORE: return "empty";
      case STORE_STATE_DIRTY_STORE: return "dirty";
      case STORE_STATE_CLEAN_STORE: return "clean";
    }
    assert(0);
    return "unknown";
}

ostream& operator<<(ostream& o, framing::cluster::StoreState s) { return o << stateName(s); }

ostream& operator<<(ostream& o, const StoreStatus& s) {
    o << s.getState();
    if (s.getState() ==  STORE_STATE_DIRTY_STORE)
        o << " cluster-id=" << s.getClusterId();
    if (s.getState() == STORE_STATE_CLEAN_STORE) {
        o << " cluster-id=" << s.getClusterId()
          << " shutdown-id=" << s.getShutdownId();
    }
    return o;
}

}} // namespace qpid::cluster

