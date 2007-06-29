#ifndef QPID_PLUGIN_H
#define QPID_PLUGIN_H

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

#include "qpid/shared_ptr.h"
#include <boost/noncopyable.hpp>
#include <vector>
#include <boost/function.hpp>


/**@file Generic plug-in framework. */

namespace qpid {
class Options;

/** Generic base class to allow dynamic casting of generic Plugin objects
 * to concrete types.
 */
struct Plugin : private boost::noncopyable {
    virtual ~Plugin() {}
};

/** Generic interface for anything that uses plug-ins. */
struct PluginUser : boost::noncopyable {
    virtual ~PluginUser() {}
    /**
     * Called by a PluginProvider to provide a plugin.
     *
     * A concrete PluginUser will dynamic_pointer_cast plugin to a
     * class it knows how to use. A PluginUser should ignore plugins
     * it does not recognize.
     *
     * The user will release its shared_ptr when it is finished using
     * plugin.
     */ 
    virtual void use(const shared_ptr<Plugin>& plugin) = 0;
};


/**
 * Base for classes that provide plug-ins.
 */
class PluginProvider : boost::noncopyable
{
  public:
    /**
     * Register the provider to appear in getProviders()
     * 
     * A concrete PluginProvider is instantiated as a global or static
     * member variable in a library so it is registered during static
     * initialization when the library is loaded.
     */
    PluginProvider();
    
    virtual ~PluginProvider();

    /**
     * Returns configuration options for the plugin.
     * Then will be updated during option parsing by the host program.
     * 
     * @return An options group or 0 for no options. Default returns 0.
     * PluginProvider retains ownership of return value.
     */
    virtual Options* getOptions();

    /** Provide plugins to a PluginUser.
     * 
     * The provider can dynamic_cast the user if it only provides
     * plugins to certain types of user. Providers should ignore
     * users they don't recognize.
     */
    virtual void provide(PluginUser& user) = 0;

    /** Get the list of pointers to the registered providers.
     * Caller must not delete the pointers.
     */
    static const std::vector<PluginProvider*>& getProviders();

  private:
    static std::vector<PluginProvider*> providers;
};
 
/** Load a shared library, registering any PluginProvider it contains.
 * 
 * This is just a convenient portable wrapper for normal shared
 * library loading. A global PluginProvider instance loaded or
 * linked in any way will get registered.
 */
void dlopen(const char* libname);

   
} // namespace qpid

#endif  /*!QPID_PLUGIN_H*/
