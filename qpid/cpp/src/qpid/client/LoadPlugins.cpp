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
#include "qpid/log/Statement.h"
#include "qpid/log/Options.h"
#include "qpid/log/Logger.h"
#include "qpid/sys/Shlib.h"
#include <boost/filesystem/operations.hpp>
#include <boost/filesystem/path.hpp>

using namespace qpid;
using namespace qpid::sys;
using namespace qpid::log;
using namespace std;
namespace fs=boost::filesystem;

struct ModuleOptions : public qpid::Options {
    string         loadDir;
    vector<string> load;
    bool           noLoad;
    ModuleOptions() : qpid::Options("Module options"), loadDir(MODULE_DIR), noLoad(false)
    {
        addOptions()
            ("module-dir",    optValue(loadDir, "DIR"),  "Load all .so modules in this directory")
            ("load-module",   optValue(load,    "FILE"), "Specifies additional module(s) to be loaded")
            ("no-module-dir", optValue(noLoad),          "Don't load modules from module directory");
    }
};

// TODO: The following is copied from qpidd.cpp - it needs to be common code
void tryShlib(const char* libname, bool noThrow) {
    try {
        Shlib shlib(libname);
        QPID_LOG (info, "Loaded Module: " << libname);
    }
    catch (const exception& e) {
        if (!noThrow)
            throw;
    }
}

void loadModuleDir (string dirname, bool isDefault)
{
    fs::path dirPath (dirname, fs::native);

    if (!fs::exists (dirPath))
    {
        if (isDefault)
            return;
        throw Exception ("Directory not found: " + dirname);
    }

    fs::directory_iterator endItr;
    for (fs::directory_iterator itr (dirPath); itr != endItr; ++itr)
    {
        if (!fs::is_directory(*itr) &&
            itr->string().find (".so") == itr->string().length() - 3)
            tryShlib (itr->string().data(), true);
    }
}

struct LoadtimeInitialise {
    LoadtimeInitialise() {
        ModuleOptions moduleOptions;
        string           defaultPath (moduleOptions.loadDir);
        moduleOptions.parse (0, 0, CONF_FILE, true);
    
        for (vector<string>::iterator iter = moduleOptions.load.begin();
             iter != moduleOptions.load.end();
             iter++)
            tryShlib (iter->data(), false);
    
        if (!moduleOptions.noLoad) {
            bool isDefault = defaultPath == moduleOptions.loadDir;
            loadModuleDir (moduleOptions.loadDir, isDefault);
        }
    }
} init;
