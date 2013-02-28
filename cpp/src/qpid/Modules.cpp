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

#include "config.h"
#include "qpid/Modules.h"
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Shlib.h"
#include "qpid/sys/FileSysDir.h"

namespace {

// CMake sets QPID_MODULE_SUFFIX; Autoconf doesn't, so assume Linux .so
#ifndef QPID_MODULE_SUFFIX
#define QPID_MODULE_SUFFIX ".so"
#endif

inline std::string& suffix() {
    static std::string s(QPID_MODULE_SUFFIX);
    return s;
}

bool isShlibName(const std::string& name) {
    return name.substr(name.size()-suffix().size()) == suffix();
}

}

namespace qpid {

ModuleOptions::ModuleOptions(const std::string& defaultModuleDir)
    : qpid::Options("Module options"), loadDir(defaultModuleDir), noLoad(false)
{
    addOptions()
        ("module-dir",    optValue(loadDir, "DIR"),  "Load all shareable modules in this directory")
        ("load-module",   optValue(load,    "FILE"), "Specifies additional module(s) to be loaded")
        ("no-module-dir", optValue(noLoad),          "Don't load modules from module directory");
}

void tryShlib(const std::string& libname) {
    sys::Shlib shlib( isShlibName(libname) ? libname : (libname + suffix()));
}

namespace {

void tryOnlyShlib(const std::string& libname) throw() {
    try {
        if (isShlibName(libname)) sys::Shlib shlib( libname );
    }
    catch (const std::exception& /*e*/) {
    }
}

}

void loadModuleDir (std::string dirname, bool isDefault)
{

    sys::FileSysDir dirPath (dirname);

    bool exists;
    try
    {
        exists = dirPath.exists();
    } catch (Exception& e) {
        throw Exception ("Invalid value for module-dir: " + e.getMessage());
    }
    if (!exists) {
        if (isDefault) return;
        throw Exception ("Directory not found: " + dirname);
    }

    dirPath.forEachFile(&tryOnlyShlib);
}

} // namespace qpid
