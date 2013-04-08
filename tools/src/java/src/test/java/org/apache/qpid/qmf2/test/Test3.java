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
package org.apache.qpid.qmf2.test;

// Misc Imports
import java.io.*;
import java.util.List;

// QMF2 Imports
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfType;
import org.apache.qpid.qmf2.common.SchemaEventClass;
import org.apache.qpid.qmf2.common.SchemaMethod;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
import org.apache.qpid.qmf2.common.SchemaProperty;

/**
 * A class used to test the Schema classes
 *
 * @author Fraser Adams
 */
public final class Test3
{
    public Test3()
    {
        try
        {
            System.out.println("*** Starting Test3 testing the various Schema classes ***");
                
            // Create and register schema for this agent.
            String packageName = "com.profitron.gizmo";

            // Declare a schema for a structured exception that can be used in failed method invocations.
            SchemaObjectClass exception = new SchemaObjectClass(packageName, "exception");

            exception.addProperty(new SchemaProperty("whatHappened", QmfType.TYPE_STRING));
            exception.addProperty(new SchemaProperty("howBad", QmfType.TYPE_INT));
            exception.addProperty(new SchemaProperty("details", QmfType.TYPE_MAP));

            // Declare a control object to test methods against.
            SchemaObjectClass control = new SchemaObjectClass(packageName, "control");
            control.addProperty(new SchemaProperty("state", QmfType.TYPE_STRING));
            control.addProperty(new SchemaProperty("methodCount", QmfType.TYPE_INT));

            SchemaMethod stopMethod = new SchemaMethod("stop", "Stop Agent");
            stopMethod.addArgument(new SchemaProperty("message", QmfType.TYPE_STRING));
            control.addMethod(stopMethod);

            SchemaMethod echoMethod = new SchemaMethod("echo", "Echo Arguments");
            echoMethod.addArgument(new SchemaProperty("sequence", QmfType.TYPE_INT, "{dir:INOUT}"));
            echoMethod.addArgument(new SchemaProperty("map", QmfType.TYPE_MAP, "{dir:INOUT}"));
            control.addMethod(echoMethod);

            SchemaMethod eventMethod = new SchemaMethod("event", "Raise an Event");
            eventMethod.addArgument(new SchemaProperty("text", QmfType.TYPE_STRING, "{dir:IN}"));
            eventMethod.addArgument(new SchemaProperty("severity", QmfType.TYPE_INT, "{dir:IN}"));
            control.addMethod(eventMethod);

            SchemaMethod failMethod = new SchemaMethod("fail", "Expected to Fail");
            failMethod.addArgument(new SchemaProperty("useString", QmfType.TYPE_BOOL, "{dir:IN}"));
            failMethod.addArgument(new SchemaProperty("stringVal", QmfType.TYPE_STRING, "{dir:IN}"));
            failMethod.addArgument(new SchemaProperty("details", QmfType.TYPE_MAP, "{dir:IN}"));
            control.addMethod(failMethod);

            SchemaMethod createMethod = new SchemaMethod("create_child", "Create Child Object");
            createMethod.addArgument(new SchemaProperty("name", QmfType.TYPE_STRING, "{dir:IN}"));
            createMethod.addArgument(new SchemaProperty("childAddr", QmfType.TYPE_MAP, "{dir:OUT}"));
            control.addMethod(createMethod);

            // Declare the child class
            SchemaObjectClass child = new SchemaObjectClass(packageName, "child");
            child.addProperty(new SchemaProperty("name", QmfType.TYPE_STRING));
    
            // Declare the event class
            SchemaEventClass event = new SchemaEventClass(packageName, "event");
            event.addProperty(new SchemaProperty("text", QmfType.TYPE_STRING));

            System.out.println("Test3 Schema classes initialised OK");

            // Now we create new instance of each class from the map encodings and list the values
            // to check everything looks OK.

            System.out.println("Test3 testing serialisation of exception schema");
            SchemaObjectClass exceptionFromMap = new SchemaObjectClass(exception.mapEncode());
            exceptionFromMap.listValues();
            System.out.println();

            System.out.println("Test3 testing serialisation of control schema");
            SchemaObjectClass controlFromMap = new SchemaObjectClass(control.mapEncode());
            controlFromMap.listValues();
            System.out.println();

            System.out.println("Test3 testing serialisation of child schema");
            SchemaObjectClass childFromMap = new SchemaObjectClass(child.mapEncode());
            childFromMap.listValues();
            System.out.println();

            System.out.println("Test3 testing serialisation of event schema");
            SchemaEventClass eventFromMap = new SchemaEventClass(event.mapEncode());
            eventFromMap.listValues();
            System.out.println();

        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + " caught: Test3 failed");
        }
    }

    public static void main(String[] args)
    {
        //System.out.println ("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        Test3 Test3 = new Test3();

        System.out.println("*** Ending Test3 ***");
    }
}
