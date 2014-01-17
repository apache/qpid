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

#include <qpid/messaging/Connection.h>
#include <qpid/messaging/Duration.h>

#define QMF_USE_DEPRECATED_API
#include <qmf/AgentSession.h>
#include <qmf/AgentEvent.h>
#include <qmf/Schema.h>
#include <qmf/SchemaProperty.h>
#include <qmf/SchemaMethod.h>
#include <qmf/Data.h>
#include <qmf/DataAddr.h>
#include <qpid/types/Variant.h>
#include <string>
#include <iostream>

using namespace std;
using namespace qmf;
using qpid::types::Variant;
using qpid::messaging::Duration;

class ExampleAgent {
public:
    ExampleAgent(const string& url);
    ~ExampleAgent();

    void setupSchema();
    void populateData();
    void run();
private:
    qpid::messaging::Connection connection;
    AgentSession session;
    Schema sch_exception;
    Schema sch_control;
    Schema sch_child;
    Schema sch_event;
    Data control;
    DataAddr controlAddr;

    bool method(AgentEvent& event);
};


ExampleAgent::ExampleAgent(const string& url)
{
    //
    // Create and open a messaging connection to a broker.
    //
    connection = qpid::messaging::Connection(url, "{reconnect:True}");
    connection.open();

    //
    // Create, configure, and open a QMFv2 agent session using the connection.
    //
    session = AgentSession(connection, "{interval:30}");
    session.setVendor("profitron.com");
    session.setProduct("gizmo");
    session.setAttribute("attr1", 2000);
    session.open();
}

ExampleAgent::~ExampleAgent()
{
    //
    // Clean up the QMF session and the AMQP connection.
    //
    session.close();
    connection.close();
}

void ExampleAgent::setupSchema()
{
    //
    // Create and register schema for this agent.
    //
    string package("com.profitron.gizmo");

    //
    // Declare a schema for a structured exception that can be used in failed
    // method invocations.
    //
    sch_exception = Schema(SCHEMA_TYPE_DATA, package, "exception");
    sch_exception.addProperty(SchemaProperty("whatHappened", SCHEMA_DATA_STRING));
    sch_exception.addProperty(SchemaProperty("howBad", SCHEMA_DATA_INT));
    sch_exception.addProperty(SchemaProperty("details", SCHEMA_DATA_MAP));

    //
    // Declare a control object to test methods against.
    //
    sch_control = Schema(SCHEMA_TYPE_DATA, package, "control");
    sch_control.addProperty(SchemaProperty("state", SCHEMA_DATA_STRING));
    sch_control.addProperty(SchemaProperty("methodCount", SCHEMA_DATA_INT));

    SchemaMethod stopMethod("stop", "{desc:'Stop Agent'}");
    stopMethod.addArgument(SchemaProperty("message", SCHEMA_DATA_STRING));
    sch_control.addMethod(stopMethod);

    SchemaMethod echoMethod("echo", "{desc:'Echo Arguments'}");
    echoMethod.addArgument(SchemaProperty("sequence", SCHEMA_DATA_INT, "{dir:INOUT}"));
    echoMethod.addArgument(SchemaProperty("map", SCHEMA_DATA_MAP, "{dir:INOUT}"));
    sch_control.addMethod(echoMethod);

    SchemaMethod eventMethod("event", "{desc:'Raise an Event'}");
    eventMethod.addArgument(SchemaProperty("text", SCHEMA_DATA_STRING, "{dir:IN}"));
    eventMethod.addArgument(SchemaProperty("severity", SCHEMA_DATA_INT, "{dir:IN}"));
    sch_control.addMethod(eventMethod);

    SchemaMethod failMethod("fail", "{desc:'Expected to Fail'}");
    failMethod.addArgument(SchemaProperty("useString", SCHEMA_DATA_BOOL, "{dir:IN}"));
    failMethod.addArgument(SchemaProperty("stringVal", SCHEMA_DATA_STRING, "{dir:IN}"));
    failMethod.addArgument(SchemaProperty("details", SCHEMA_DATA_MAP, "{dir:IN}"));
    sch_control.addMethod(failMethod);

    SchemaMethod createMethod("create_child", "{desc:'Create Child Object'}");
    createMethod.addArgument(SchemaProperty("name", SCHEMA_DATA_STRING, "{dir:IN}"));
    createMethod.addArgument(SchemaProperty("childAddr", SCHEMA_DATA_MAP, "{dir:OUT}"));
    sch_control.addMethod(createMethod);

    //
    // Declare the child class
    //
    sch_child = Schema(SCHEMA_TYPE_DATA, package, "child");
    sch_child.addProperty(SchemaProperty("name", SCHEMA_DATA_STRING));

    //
    // Declare the event class
    //
    sch_event = Schema(SCHEMA_TYPE_EVENT, package, "event");
    sch_event.addProperty(SchemaProperty("text", SCHEMA_DATA_STRING));

    //
    // Register our schemata with the agent session.
    //
    session.registerSchema(sch_exception);
    session.registerSchema(sch_control);
    session.registerSchema(sch_child);
    session.registerSchema(sch_event);
}

void ExampleAgent::populateData()
{
    //
    // Create a control object and give it to the agent session to manage.
    //
    control = Data(sch_control);
    control.setProperty("state", "OPERATIONAL");
    control.setProperty("methodCount", 0);
    controlAddr = session.addData(control, "singleton");
}

void ExampleAgent::run()
{
    AgentEvent event;
    bool running(true);

    while (running) {
        bool valid(session.nextEvent(event, Duration::SECOND));
        if (valid && running) {
            switch (event.getType()) {
            case AGENT_METHOD:
                running = method(event);
                break;
            default:
                break;
            }
        }
    }
}

bool ExampleAgent::method(AgentEvent& event)
{
    const string& name(event.getMethodName());
    control.setProperty("methodCount", control.getProperty("methodCount").asUint32() + 1);

    try {
        if (controlAddr == event.getDataAddr()) {
            if (name == "stop") {
                cout << "Stopping: message=" << event.getArguments()["message"] << endl;
                session.methodSuccess(event);
                return false;
            }

            if (name == "echo") {
                event.addReturnArgument("sequence", event.getArguments()["sequence"]);
                event.addReturnArgument("map", event.getArguments()["map"]);
                session.methodSuccess(event);
                return true;
            }

            if (name == "event") {
                Data ev(sch_event);
                ev.setProperty("text", event.getArguments()["text"]);
                session.raiseEvent(ev, event.getArguments()["severity"]);
                session.methodSuccess(event);
                return true;
            }

            if (name == "fail") {
                if (event.getArguments()["useString"])
                    session.raiseException(event, event.getArguments()["stringVal"]);
                else {
                    Data ex(sch_exception);
                    ex.setProperty("whatHappened", "It Failed");
                    ex.setProperty("howBad", 75);
                    ex.setProperty("details", event.getArguments()["details"]);
                    session.raiseException(event, ex);
                }
            }

            if (name == "create_child") {
                const string& name(event.getArguments()["name"]);
                Data child(sch_child);
                child.setProperty("name", name);
                DataAddr addr(session.addData(child, name));
                event.addReturnArgument("childAddr", addr.asMap());
                session.methodSuccess(event);
            }
        }
    } catch (const exception& e) {
        //
        // Pass the exception on to the caller.
        //
        session.raiseException(event, e.what());
    }

    return true;
}

int main()
{
    ExampleAgent agent("localhost");
    agent.setupSchema();
    agent.populateData();
    agent.run();
}

