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
 *
 */

#include "Plugin.h"
#include <qpid/shared_ptr.h>
#include <qpid/Options.h>
#include <qpid/sys/Mutex.h>

namespace qpid {

Plugin::Target::~Target() {}

void Plugin::Target::createPlugins() {
    typedef std::vector<Plugin::Factory*>::const_iterator Iter; 
    for (Iter i = Factory::getList().begin(); i != Factory::getList().end(); ++i) {
        boost::shared_ptr<Plugin> plugin = (**i).create(*this);
        if (plugin)
            plugins.push_back(plugin);
    }
}

void Plugin::Target::initializePlugins() {
    typedef std::vector<boost::shared_ptr<Plugin> >::iterator Iter; 
    for (Iter i = plugins.begin(); i != plugins.end(); ++i) 
        (**i).initialize(*this);
}

void Plugin::Target::releasePlugins() { plugins.clear(); }

Plugin::Factory::~Factory() {}

Plugin::Factory::Factory() {
    const_cast<std::vector<Plugin::Factory*>& >(getList()).push_back(this);
}

static sys::PODMutex PluginFactoryGetListLock;

const std::vector<Plugin::Factory*>& Plugin::Factory::getList() {
    sys::PODMutex::ScopedLock l(PluginFactoryGetListLock);
    static std::vector<Plugin::Factory*> list;
    return list;
}

void Plugin::Factory::addOptions(Options& opts) {
    typedef std::vector<Plugin::Factory*>::const_iterator Iter; 
    for (Iter i = Factory::getList().begin(); i != Factory::getList().end(); ++i) {
        if ((**i).getOptions())
            opts.add(*(**i).getOptions());
    }
}

Plugin::~Plugin() {}

} // namespace qpid
