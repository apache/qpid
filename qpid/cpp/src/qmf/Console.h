#ifndef _QmfConsole_
#define _QmfConsole_

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
 */

#include <qmf/ManagedConnection.h>
#include <qmf/Agent.h>
#include <qmf/Broker.h>
#include <qmf/Package.h>
#include <qmf/SchemaClassTable.h>
#include <qmf/Object.h>
#include <qmf/ConsoleHandler.h>
#include <set>
#include <vector>
#include <string>

namespace qmf {

    struct ConsoleSettings {
        bool rcvObjects;
        bool rcvEvents;
        bool rcvHeartbeats;
        bool userBindings;
        uint32_t methodTimeout;
        uint32_t getTimeout;

        ConsoleSettings() :
            rcvObjects(true),
            rcvEvents(true),
            rcvHeartbeats(true),
            userBindings(false),
            methodTimeout(20),
            getTimeout(20) {}
    };

    class Console {
    public:
        Console(ConsoleHandler* handler = 0, ConsoleSettings settings = ConsoleSettings());
        ~Console();

        Broker* addConnection(ManagedConnection& connection);
        void delConnection(Broker* broker);
        void delConnection(ManagedConnection& connection);

        const PackageMap& getPackages() const;

        void bindPackage(const Package& package);
        void bindPackage(const std::string& packageName);
        void bindClass(const SchemaClass& otype);
        void bindClass(const std::string& packageName, const std::string& className);

        void getAgents(std::set<Agent>& agents, Broker* = 0);
        void getObjects(std::vector<Object>& objects, const std::string& typeName,
                        const std::string& packageName = "",
                        Broker* broker = 0,
                        Agent* agent = 0);
        void getObjects(std::vector<Object>& objects,
                        const std::map<std::string, std::string>& query,
                        Broker* broker = 0,
                        Agent* agent = 0);
    };
}

#endif

