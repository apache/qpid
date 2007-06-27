/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "Plugin.h"

namespace qpid {
namespace broker {

std::vector<Plugin*> Plugin::plugins;

Plugin::Plugin() {
    // Register myself.
    plugins.push_back(this);
}

Plugin::~Plugin() {}

Options*  Plugin::getOptions() { return 0; }

void  Plugin::start(Broker&) {}

void  Plugin::finish(Broker&) {}

const std::vector<Plugin*>& Plugin::getPlugins() { return plugins; }

#ifdef USE_APR_PLATFORM

#include "qpid/sys/apr/APRBase.h"
#include "qpid/sys/apr/APRPool.h"
#include <apr_dso.h>

void Plugin::dlopen(const std::string& name) {
    apr_dso_handle_t* handle;
    CHECK_APR_SUCCESS(
        apr_dso_load(&handle, name.c_str(), sys::APRPool::get()));
}

#else // Posix

#include <dlfcn.h>

void Plugin::dlopen(const std::string& name) {
    dlerror();
    dlopen(name.c_str(), RTLD_NOW);
    const char* error = dlerror();
    if (error) {
        THROW_QPID_ERROR(INTERNAL_ERROR, error);
    }
}
#endif // USE_APR_PLATFORM

}} // namespace qpid::broker
