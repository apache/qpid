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
namespace fs=boost::filesystem;
using std::ostream;

StoreStatus::StoreStatus(const std::string& d)
    : state(STORE_STATE_NO_STORE), dataDir(d), configSeq(0)
{}

namespace {

const char* SUBDIR="cluster";
const char* CLUSTER_ID_FILE="cluster.uuid";
const char* SHUTDOWN_ID_FILE="shutdown.uuid";
const char* CONFIG_SEQ_FILE="config.seq";

Uuid loadUuid(const fs::path& path) {
    Uuid ret;
    if (exists(path)) {
        fs::ifstream i(path);
        i >> ret;
    }
    return ret;
}

void saveUuid(const fs::path& path, const Uuid& uuid) {
    fs::ofstream o(path);
    o << uuid;
}

} // namespace


void StoreStatus::load() {
    fs::path dir = fs::path(dataDir, fs::native)/SUBDIR;
    try {
        create_directory(dir);
        clusterId = loadUuid(dir/CLUSTER_ID_FILE);
        shutdownId = loadUuid(dir/SHUTDOWN_ID_FILE);
        fs::ifstream is(dir/CONFIG_SEQ_FILE);
        uint32_t n;
        is >> n;
        configSeq = framing::SequenceNumber(n);
        if (clusterId && shutdownId) state = STORE_STATE_CLEAN_STORE;
        else if (clusterId) state = STORE_STATE_DIRTY_STORE;
        else state = STORE_STATE_EMPTY_STORE;
    }
    catch (const std::exception&e) {
        throw Exception(QPID_MSG("Cannot load cluster store status: " << e.what()));
    }
}

void StoreStatus::save() {
    fs::path dir = fs::path(dataDir, fs::native)/SUBDIR;
    try {
        create_directory(dir);
        saveUuid(dir/CLUSTER_ID_FILE, clusterId);
        saveUuid(dir/SHUTDOWN_ID_FILE, shutdownId);
        fs::ofstream os(dir/CONFIG_SEQ_FILE);
        os << configSeq.getValue();
    }
    catch (const std::exception&e) {
        throw Exception(QPID_MSG("Cannot save cluster store status: " << e.what()));
    }
}

void StoreStatus::dirty(const Uuid& clusterId_) {
    assert(clusterId_);
    clusterId = clusterId_;
    shutdownId = Uuid();
    state = STORE_STATE_DIRTY_STORE;
    save();
}

void StoreStatus::clean(const Uuid& shutdownId_) {
    assert(shutdownId_);
    state = STORE_STATE_CLEAN_STORE;
    shutdownId = shutdownId_;
    save();
}

void StoreStatus::setConfigSeq(framing::SequenceNumber seq) {
    configSeq = seq;
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
        o << " cluster-id=" << s.getClusterId()
          << " config-sequence=" << s.getConfigSeq();
    if (s.getState() == STORE_STATE_CLEAN_STORE) {
        o << " cluster-id=" << s.getClusterId()
          << " shutdown-id=" << s.getShutdownId();
    }
    return o;
}

}} // namespace qpid::cluster

