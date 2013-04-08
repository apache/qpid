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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// QMF2 Imports
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfQuery;
import org.apache.qpid.qmf2.common.QmfQueryTarget;
import org.apache.qpid.qmf2.common.QmfType;
import org.apache.qpid.qmf2.common.SchemaClass;
import org.apache.qpid.qmf2.common.SchemaClassId;
import org.apache.qpid.qmf2.common.SchemaEventClass;
import org.apache.qpid.qmf2.common.SchemaMethod;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
import org.apache.qpid.qmf2.common.SchemaProperty;

import org.apache.qpid.qmf2.console.QmfConsoleData;
import org.apache.qpid.qmf2.agent.QmfAgentData;


/**
 * A class used to test the QmfQuery classes
 *
 * @author Fraser Adams
 */
public final class Test4
{

    private Map<SchemaClassId, SchemaClass> _schemaCache = new ConcurrentHashMap<SchemaClassId, SchemaClass>();
    private Map<ObjectId, QmfAgentData> _objectIndex = new ConcurrentHashMap<ObjectId, QmfAgentData>();

    public Test4()
    {
        try
        {
            System.out.println("*** Starting Test4 testing the QmfQuery class ***");
                
            // Create and register schema for this agent.
            String packageName = "com.fadams.qmf2";

            // Declare a mammal class to test against.
            SchemaObjectClass mammal = new SchemaObjectClass(packageName, "mammal");
            mammal.addProperty(new SchemaProperty("name", QmfType.TYPE_STRING));
            mammal.addProperty(new SchemaProperty("legs", QmfType.TYPE_INT));
            mammal.setIdNames("name");

            // Declare an insect class to test against.
            SchemaObjectClass insect = new SchemaObjectClass(packageName, "insect");
            insect.addProperty(new SchemaProperty("name", QmfType.TYPE_STRING));
            insect.addProperty(new SchemaProperty("legs", QmfType.TYPE_INT));
            insect.setIdNames("name");

            // Declare a reptile class to test against.
            SchemaObjectClass reptile = new SchemaObjectClass(packageName, "reptile");
            reptile.addProperty(new SchemaProperty("name", QmfType.TYPE_STRING));
            reptile.addProperty(new SchemaProperty("legs", QmfType.TYPE_INT));
            reptile.setIdNames("name");

            // Declare a bird class to test against.
            SchemaObjectClass bird = new SchemaObjectClass(packageName, "bird");
            bird.addProperty(new SchemaProperty("name", QmfType.TYPE_STRING));
            bird.addProperty(new SchemaProperty("legs", QmfType.TYPE_INT));
            bird.setIdNames("name");


            registerObjectClass(mammal);
            registerObjectClass(insect);
            registerObjectClass(reptile);
            registerObjectClass(bird);

            QmfAgentData cat = new QmfAgentData(mammal);
            cat.setValue("name", "cat");
            cat.setValue("legs", 4l);
            addObject(cat);

            QmfAgentData dog = new QmfAgentData(mammal);
            dog.setValue("name", "dog");
            dog.setValue("legs", 4l);
            addObject(dog);

            QmfAgentData rabbit = new QmfAgentData(mammal);
            rabbit.setValue("name", "rabbit");
            rabbit.setValue("legs", 4);
            addObject(rabbit);

            QmfAgentData horse = new QmfAgentData(mammal);
            horse.setValue("name", "horse");
            horse.setValue("legs", 4);
            addObject(horse);

            QmfAgentData human = new QmfAgentData(mammal);
            human.setValue("name", "human");
            human.setValue("legs", 2);
            addObject(human);


            QmfAgentData wasp = new QmfAgentData(insect);
            wasp.setValue("name", "wasp");
            wasp.setValue("legs", 6);
            addObject(wasp);

            QmfAgentData ant = new QmfAgentData(insect);
            ant.setValue("name", "ant");
            ant.setValue("legs", 6);
            addObject(ant);

            QmfAgentData crocodile = new QmfAgentData(reptile);
            crocodile.setValue("name", "crocodile");
            crocodile.setValue("legs", 4);
            addObject(crocodile);

            QmfAgentData gecko = new QmfAgentData(reptile);
            gecko.setValue("name", "gecko");
            gecko.setValue("legs", 4);
            addObject(gecko);

            QmfAgentData python = new QmfAgentData(reptile);
            python.setValue("name", "python");
            python.setValue("legs", 0);
            addObject(python);

            QmfAgentData hawk = new QmfAgentData(bird);
            hawk.setValue("name", "hawk");
            hawk.setValue("legs", 2);
            addObject(hawk);

            QmfAgentData ostrich = new QmfAgentData(bird);
            ostrich.setValue("name", "ostrich");
            ostrich.setValue("legs", 2);
            addObject(ostrich);


            System.out.println("total number of objects registered: " + _objectIndex.size());

            QmfQuery query;
            List<QmfConsoleData> results;

            System.out.println("looking up wasp object by ID");
            query = new QmfQuery(QmfQueryTarget.OBJECT, wasp.getObjectId());
            results = evaluateDataQuery(query);
            displayResults(results);

            System.out.println("\nlooking up mammal objects");
            query = new QmfQuery(QmfQueryTarget.OBJECT, new SchemaClassId("mammal"));
            results = evaluateDataQuery(query);
            displayResults(results);

            System.out.println("\nlooking up everything in package com.fadams.qmf2");
            query = new QmfQuery(QmfQueryTarget.OBJECT, new SchemaClassId("com.fadams.qmf2", null));
            results = evaluateDataQuery(query);
            displayResults(results);


            System.out.println("\nQuery for all mammals with more than two legs");
            String predicate = "['and', ['eq', '_package_name', ['quote', 'com.fadams.qmf2']], " +
                                       "['eq', '_class_name', ['quote', 'mammal']], " +
                                       "['gt', 'legs', 2]]";

            //predicate = "['eq', '_package_name', ['quote', 'com.fadams.qmf2']]";

            //predicate = "[]";

            query = new QmfQuery(QmfQueryTarget.OBJECT, predicate);
            System.out.println(query.getPredicate());

            results = evaluateDataQuery(query);
            displayResults(results);


            System.out.println("\nQuery for everything with less than four legs");
            predicate = "['lt', 'legs', 4]";

            query = new QmfQuery(QmfQueryTarget.OBJECT, predicate);
            System.out.println(query.getPredicate());

            results = evaluateDataQuery(query);
            displayResults(results);


            System.out.println("\nQuery for everything with between two and four legs");
            predicate = "['and', ['ge', 'legs', 2], " +
                                "['le', 'legs', 4]]";

            query = new QmfQuery(QmfQueryTarget.OBJECT, predicate);
            System.out.println(query.getPredicate());

            results = evaluateDataQuery(query);
            displayResults(results);


            System.out.println("\nQuery for all reptiles or birds");
            predicate = "['or', ['eq', '_class_name', ['quote', 'reptile']], " +
                               "['eq', '_class_name', ['quote', 'bird']]]";

            query = new QmfQuery(QmfQueryTarget.OBJECT, predicate);
            System.out.println(query.getPredicate());

            results = evaluateDataQuery(query);
            displayResults(results);


            System.out.println("\nQuery for everything whose name matches the regex ^h");
            predicate = "['re_match', 'name', ['quote', '^h']]";

            query = new QmfQuery(QmfQueryTarget.OBJECT, predicate);
            System.out.println(query.getPredicate());

            results = evaluateDataQuery(query);
            displayResults(results);


        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + " caught: Test4 failed");
        }
    }

    public void registerObjectClass(SchemaObjectClass schema)
    {
        SchemaClassId classId = schema.getClassId();
        _schemaCache.put(classId, schema);
    }

    public void addObject(QmfAgentData object) throws QmfException
    {
        SchemaClassId classId = object.getSchemaClassId();
        SchemaClass schema = _schemaCache.get(classId);

        // Try to create an objectName using the set of property names that have been specified as idNames in the schema
        StringBuilder buf = new StringBuilder();
        if (schema != null && schema instanceof SchemaObjectClass)
        {
            String[] idNames = ((SchemaObjectClass)schema).getIdNames();
            for (String name : idNames)
            {
                buf.append(object.getStringValue(name));
            }
        }
        String objectName = buf.toString();

        // If the schema hasn't given any help we use a UUID
        if (objectName.length() == 0) objectName = UUID.randomUUID().toString();

        // Finish up the name by incorporating package and class names
        objectName = classId.getPackageName() + ":" + classId.getClassName() + ":" + objectName;

        // Now we've got a good name for the object we create it's ObjectId and add that to the object
        ObjectId addr = new ObjectId("test"/*name*/, objectName, 0/*epoch*/);
        object.setObjectId(addr);

        if (_objectIndex.get(addr) != null)
        {
            throw new QmfException("Duplicate QmfAgentData Address");
        }

        _objectIndex.put(addr, object);
    }


    public List<QmfConsoleData> evaluateDataQuery(QmfQuery query)
    {
        List<QmfConsoleData> results = new ArrayList<QmfConsoleData>();

        if (query.getObjectId() != null)
        {
            // Look up a QmfAgentData object by the ObjectId obtained from the query
            ObjectId objectId = query.getObjectId();
            QmfAgentData object = _objectIndex.get(objectId);
            if (object != null && !object.isDeleted())
            {
                results.add(new QmfConsoleData(object.mapEncode(), null));
            }
        }
        else
        {
            for (QmfAgentData object : _objectIndex.values())
            {
                if (!object.isDeleted() && query.evaluate(object))
                {
                    results.add(new QmfConsoleData(object.mapEncode(), null));
                }
            }
        }

        return results;
    }

    public List<SchemaClass> evaluateSchemaQuery(QmfQuery query)
    {
        return null;
    }


    public void displayResults(List<QmfConsoleData> values)
    {
        for (QmfConsoleData object : values)
        {
            System.out.println("name = " + object.getStringValue("name") + ", legs = " + object.getLongValue("legs"));
        }
    }

    public static void main(String[] args)
    {
        //System.out.println ("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        Test4 Test4 = new Test4();

        System.out.println("*** Ending Test4 ***");
    }
}
