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

std::vector<PluginProvider*> PluginProvider::providers;

PluginProvider::PluginProvider() {
    // Register myself.
    providers.push_back(this);
}

PluginProvider::~PluginProvider() {}

Options*  PluginProvider::getOptions() { return 0; }

const std::vector<PluginProvider*>& PluginProvider::getProviders() {
    return providers;
}
} // namespace qpid

// TODO aconway 2007-06-28: GNU lib has portable dlopen if we go that way.

#ifdef USE_APR_PLATFORM

#include "qpid/sys/apr/APRBase.h"
#include "qpid/sys/apr/APRPool.h"
#include <apr_dso.h>

namespace qpid {
void dlopen(const char* name) {
    apr_dso_handle_t* handle;
    CHECK_APR_SUCCESS(
        apr_dso_load(&handle, name, sys::APRPool::get()));
}
} // namespace qpid

#else // Posix

#include "qpid/sys/posix/check.h"
#include <dlfcn.h>

namespace qpid {
void dlopen(const char* name) {
    ::dlerror();
    ::dlopen(name, RTLD_NOW);
    const char* error = ::dlerror();
    if (error) {
        THROW_QPID_ERROR(INTERNAL_ERROR, error);
    }
}
} // namespace qpidpp

#endif // USE_APR_PLATFORM
