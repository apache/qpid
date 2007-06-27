#ifndef QPID_BROKER_PLUGIN_H
#define QPID_BROKER_PLUGIN_H

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

#include "qpid/Options.h"
#include "qpid/broker/Broker.h"
#include <boost/noncopyable.hpp>
#include <vector>

namespace qpid {
namespace broker {

/**
 * Inherit broker plug-ins from this class, override the virtual
 * functions and create a global (or class static member) instance in
 * a shared library. When the library is loaded your plug-in will be
 * registered.
 */
class Plugin : boost::noncopyable
{
  public:
    /** Constructor registers the plugin to appear in getPlugins().
     * Note: Plugin subclasses should only be constructed during
     * static initialization, i.e. they should only be declared
     * as global or static member variables.
     */
    Plugin();
    
    virtual ~Plugin();

    /**
     * Override if your plugin has configuration options.
     * They will be included in options parsing prior to broker
     * creation setup.
     *@return An options group or 0. Default returns 0.
     */
    virtual Options* getOptions();

    /** Called immediately after broker creation to allow plug-ins
     * to do whatever they do to the broker, e.g. add handler chain
     * manipulators.
     */
    virtual void start(Broker& b);

    /** Called just before broker shutdown. Default does nothing */
    virtual void finish(Broker& b);

    /** Get the list of registered plug-ins. */
    static const std::vector<Plugin*>& getPlugins();

    /** Load a shared library (that contains plugins presumably!) */
    static void dlopen(const std::string& libname);
    
  private:
    static std::vector<Plugin*> plugins;
};
    
}} // namespace qpid::broker




#endif  /*!QPID_BROKER_PLUGIN_H*/
