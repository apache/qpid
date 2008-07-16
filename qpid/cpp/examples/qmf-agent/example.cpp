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

#include <qpid/management/Manageable.h>
#include <qpid/management/ManagementObject.h>
#include <qpid/agent/ManagementAgent.h>
#include <qpid/agent/ManagementAgentImpl.h>
#include "Parent.h"
#include "PackageQmf_example.h"

#include <unistd.h>
#include <cstdlib>
#include <iostream>

#include <sstream>

using namespace qpid::management;
using namespace std;

//==============================================================
// CoreClass is the operational class that corresponds to the
// "Parent" class in the management schema.
//==============================================================
class CoreClass : public Manageable
{
    string  name;
    Parent* mgmtObject;

public:

    CoreClass(ManagementAgent* agent, string _name);
    ~CoreClass() {}

    void bumpCounter() { mgmtObject->inc_count(); }

    ManagementObject* GetManagementObject(void) const
    { return mgmtObject; }
};

CoreClass::CoreClass(ManagementAgent* agent, string _name) : name(_name)
{
    mgmtObject = new Parent(agent, this, name);

    agent->addObject(mgmtObject);
    mgmtObject->set_state("IDLE");
}


//==============================================================
// Main program
//==============================================================
int main(int argc, char** argv) {
    ManagementAgent::Singleton singleton;
    const char* host = argc>1 ? argv[1] : "127.0.0.1";
    int port = argc>2 ? atoi(argv[2]) : 5672;

    // Create the qmf management agent
    ManagementAgent* agent = singleton.getInstance();

    // Register the Qmf_example schema with the agent
    PackageQmf_example packageInit(agent);

    // Start the agent.  It will attempt to make a connection to the
    // management broker
    agent->init (string(host), port);

    // Allocate some core objects
    CoreClass core1(agent, "Example Core Object #1");
    CoreClass core2(agent, "Example Core Object #2");
    CoreClass core3(agent, "Example Core Object #3");

    // Periodically bump a counter in core1 to provide a changing statistical value
    while (1)
    {
        sleep(1);
        core1.bumpCounter();
    }
}


