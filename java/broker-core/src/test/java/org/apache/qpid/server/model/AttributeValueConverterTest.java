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
package org.apache.qpid.server.model;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.Collections.unmodifiableList;

import static org.apache.qpid.server.model.AttributeValueConverter.getConverter;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.qpid.server.model.testmodel.TestModel;
import org.apache.qpid.server.model.testmodel.TestRootCategory;

public class AttributeValueConverterTest extends TestCase
{
    private final ConfiguredObjectFactory _objectFactory = TestModel.getInstance().getObjectFactory();
    private final Map<String, Object> _attributes = new HashMap<>();
    private final Map<String, String> _context = new HashMap<>();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _attributes.put(ConfiguredObject.NAME, "objectName");
        _attributes.put(ConfiguredObject.CONTEXT, _context);
    }

    public void testMapConverter()
    {
        _context.put("simpleMap", "{\"a\" : \"b\"}");
        _context.put("mapWithInterpolatedContents", "{\"${mykey}\" : \"b\"}");
        _context.put("mykey", "mykey1");

        ConfiguredObject object = _objectFactory.create(TestRootCategory.class, _attributes);

        AttributeValueConverter<Map> mapConverter = getConverter(Map.class, Map.class);

        Map<String, String> nullMap = mapConverter.convert(null, object);
        assertNull(nullMap);

        Map<String, String> emptyMap = mapConverter.convert("{ }", object);
        assertEquals(emptyMap(), emptyMap);

        Map<String, String> map = mapConverter.convert("{\"a\" : \"b\"}", object);
        assertEquals(singletonMap("a", "b"), map);

        Map<String, String> mapFromInterpolatedVar = mapConverter.convert("${simpleMap}", object);
        assertEquals(singletonMap("a", "b"), mapFromInterpolatedVar);

        Map<String, String> mapFromInterpolatedVarWithInterpolatedContents =
                mapConverter.convert("${mapWithInterpolatedContents}", object);
        assertEquals(singletonMap("mykey1", "b"), mapFromInterpolatedVarWithInterpolatedContents);

        try
        {
            mapConverter.convert("not a map", object);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException e)
        {
            // PASS
        }
    }

    public void testNonGenericCollectionConverter()
    {
        _context.put("simpleCollection", "[\"a\", \"b\"]");

        ConfiguredObject object = _objectFactory.create(TestRootCategory.class, _attributes);

        AttributeValueConverter<Collection> collectionConverter = getConverter(Collection.class, Collection.class);

        Collection<String> nullCollection = collectionConverter.convert(null, object);
        assertNull(nullCollection);

        Collection<String> emptyCollection = collectionConverter.convert("[ ]", object);
        assertTrue(emptyCollection.isEmpty());

        Collection<String> collection = collectionConverter.convert("[\"a\",  \"b\"]", object);
        assertEquals(2, collection.size());
        assertTrue(collection.contains("a"));
        assertTrue(collection.contains("b"));

        Collection<String> collectionFromInterpolatedVar = collectionConverter.convert("${simpleCollection}", object);
        assertEquals(2, collectionFromInterpolatedVar.size());
        assertTrue(collectionFromInterpolatedVar.contains("a"));
        assertTrue(collectionFromInterpolatedVar.contains("b"));

        try
        {
            collectionConverter.convert("not a collection", object);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException e)
        {
            // PASS
        }
    }

    public void testNonGenericListConverter()
    {
        _context.put("simpleList", "[\"a\", \"b\"]");

        ConfiguredObject object = _objectFactory.create(TestRootCategory.class, _attributes);

        AttributeValueConverter<List> listConverter = getConverter(List.class, List.class);

        List<String> nullList = listConverter.convert(null, object);
        assertNull(nullList);

        List<String> emptyList = listConverter.convert("[ ]", object);
        assertTrue(emptyList.isEmpty());

        List<String> expectedList = unmodifiableList(asList("a", "b"));

        List<String> list = listConverter.convert("[\"a\",  \"b\"]", object);
        assertEquals(expectedList, list);

        List<String> listFromInterpolatedVar = listConverter.convert("${simpleList}", object);
        assertEquals(expectedList, listFromInterpolatedVar);

        try
        {
            listConverter.convert("not a list", object);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException e)
        {
            // PASS
        }
    }

    public void testNonGenericSetConverter()
    {
        _context.put("simpleSet", "[\"a\", \"b\"]");

        ConfiguredObject object = _objectFactory.create(TestRootCategory.class, _attributes);

        AttributeValueConverter<Set> setConverter = getConverter(Set.class, Set.class);;

        Set<String> nullSet = setConverter.convert(null, object);
        assertNull(nullSet);

        Set<String> emptySet = setConverter.convert("[ ]", object);
        assertTrue(emptySet.isEmpty());

        Set<String> expectedSet = unmodifiableSet(new HashSet<>(asList("a", "b")));

        Set<String> set = setConverter.convert("[\"a\",  \"b\"]", object);
        assertEquals(expectedSet, set);

        Set<String> setFromInterpolatedVar = setConverter.convert("${simpleSet}", object);
        assertEquals(expectedSet, setFromInterpolatedVar);

        try
        {
            setConverter.convert("not a set", object);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException e)
        {
            // PASS
        }
    }

}