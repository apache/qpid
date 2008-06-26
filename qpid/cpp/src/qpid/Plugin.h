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

#include <boost/shared_ptr.hpp>
#include <vector>

/**@file Generic plug-in framework. */

namespace qpid {

class Options;

/**
 * Plug-in base class.
 *
 * A Plugin is created by a Plugin::Factory and attached to a Plugin::Target.
 */
class Plugin {
  public:
    /**
     * Base class for target objects that receive plug-ins.
     */
    class Target {
      public:
        virtual ~Target();

      protected:
        /** For each Factory create a plugin attached to this */
        void createPlugins();

        /** Initialize all attached plugins */
        void initializePlugins();
        
        /** Release the attached plugins. Done automatically in destructor. */
        void releasePlugins();

      private:
        std::vector<boost::shared_ptr<Plugin> > plugins;
    };

    /** Base class for a factory to create Plugins. */
    class Factory {
      public:
        /**
         * Constructor registers the factory so it will be included in getList().
         *
         * Derive your Plugin Factory class from Factory and create a
         * single global instance in your plug-in library. Loading the
         * library will automatically register your factory.
         */
        Factory();
        
        virtual ~Factory();

        /** Get the list of Factories */
        static const std::vector<Factory*>& getList();

        /** For each Factory in Factory::getList() add options to opts. */
        static void addOptions(Options& opts);

        /**
         * Configuration options for this factory.
         * Then will be updated during option parsing by the host program.
         * 
         * @return An options group or 0 for no options.
         */
        virtual Options* getOptions() = 0;
        
        /**
         * Create a Plugin for target.
         * Uses option values set by getOptions().
         * Target may not be fully initialized at this point.
         * Actions that require a fully-initialized target should be
         * done in initialize().
         * 
         * @returns 0 if the target type is not compatible with this Factory.
         */
        virtual boost::shared_ptr<Plugin> create(Target& target) = 0;
    };

    /**
     * Template factory implementation, checks target type is correct. 
     */
    template <class TargetType> class FactoryT : public Factory {
        Options* getOptions() { return 0; }

        virtual boost::shared_ptr<Plugin> createT(TargetType& target) = 0;

        boost::shared_ptr<Plugin> create(Target& target) {
            TargetType* tt = dynamic_cast<TargetType*>(&target);
            if (tt)
                return createT(*tt);
            else
                return boost::shared_ptr<Plugin>();
        }
    };

    // Plugin functions.

    virtual ~Plugin();
    
    /**
     * Initialize the Plugin. 
     * Called after the target is fully initialized.
     */
    virtual void initialize(Target&) = 0;
};

/** Template plugin factory */
template <class TargetType> class PluginT : public Plugin {

    virtual void initializeT(TargetType&) = 0;

    void initialize(Plugin::Target& target) {
        TargetType* tt = dynamic_cast<TargetType*>(&target);
        assert(tt);
        initializeT(*tt);
    }
};

} // namespace qpid

#endif  /*!QPID_PLUGIN_H*/
