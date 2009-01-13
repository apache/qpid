#ifndef QPID_PLUGIN_H
#define QPID_PLUGIN_H

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

#include <boost/noncopyable.hpp>
#include <boost/function.hpp>
#include <vector>

/**@file Generic plug-in framework. */

namespace qpid {
struct Options;

/**
 * Plug-in base class.
 */
class Plugin : private boost::noncopyable {
  public:
    typedef std::vector<Plugin*> Plugins;
    
    /**
     * Base interface for targets that can receive plug-ins.
     * Also allows plug-ins to attach a a function to be called
     * when the target is 'finalized'.
     */
    class Target : private boost::noncopyable
    {
      public:
        /** Calls finalize() if not already called. */
        virtual ~Target();

        /** Run all the finalizers */
        void finalize();

        /** Add a function to run when finalize() is called */
        void addFinalizer(const boost::function<void()>&);

      private:
        std::vector<boost::function<void()> > finalizers;
    };

    /**
     * Constructor registers the plug-in to appear in getPlugins().
     * 
     * A concrete Plugin is instantiated as a global or static
     * member variable in a library so it is registered during 
     * initialization when the library is loaded.
     */
    Plugin();
    
    virtual ~Plugin();

    /**
     * Configuration options for the plugin.
     * Then will be updated during option parsing by the host program.
     * 
     * @return An options group or 0 for no options. Default returns 0.
     * Plugin retains ownership of return value.
     */
    virtual Options* getOptions();

    /**
     * Initialize Plugin functionality on a Target, called before
     * initializing the target.
     *
     * Plugins should ignore targets they don't recognize.
     *
     * Called before the target itself is initialized.
     */
    virtual void earlyInitialize(Target&) = 0;

    /**
     * Initialize Plugin functionality on a Target. Called after
     * initializing the target.
     * 
     * Plugins should ignore targets they don't recognize.
     *
     * Called after the target is fully initialized.
     */
    virtual void initialize(Target&) = 0;

    /** List of registered Plugin objects.
     * Caller must not delete plugin pointers.
     */
    static const Plugins& getPlugins();

    /** Call earlyInitialize() on all registered plugins */
    static void earlyInitAll(Target&);

    /** Call initialize() on all registered plugins */
    static void initializeAll(Target&);

    /** For each registered plugin, add plugin.getOptions() to opts. */
    static void addOptions(Options& opts);
};
 
} // namespace qpid

#endif  /*!QPID_PLUGIN_H*/
