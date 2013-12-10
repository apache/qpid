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
package org.apache.qpid.disttest.message;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.qpid.disttest.client.property.ListPropertyValue;
import org.apache.qpid.disttest.client.property.PropertyValue;
import org.apache.qpid.disttest.json.JsonHandler;
import org.apache.qpid.test.utils.QpidTestCase;

public class JsonHandlerTest extends QpidTestCase
{
    private JsonHandler _jsonHandler = null;
    private SendChristmasCards _testCommand = null;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _jsonHandler = new JsonHandler();

        _testCommand = new SendChristmasCards(CommandType.START_TEST, Collections.singletonMap(SendChristmasCards.CardType.FUNNY, 5));
        _testCommand.persons = Arrays.asList(new Person("Phil"), new Person("Andrew"));
    }

    public void testMarshallUnmarshall() throws Exception
    {
        final String jsonString = _jsonHandler.marshall(_testCommand);

        final SendChristmasCards unmarshalledCommand = _jsonHandler.unmarshall(jsonString, SendChristmasCards.class);

        assertEquals("Unmarshalled command should be equal to the original object", _testCommand, unmarshalledCommand);
    }

    public void testGeneratorDesrialization()
    {
        String json = "{_messageProperties: {test: 1; generator: {'@def': 'list'; _cyclic: false; _items: ['first', " +
                "{'@def': 'range'; _upper:10; '_type':'int'}]}}}";
        final TestCommand unmarshalledCommand = _jsonHandler.unmarshall(json, TestCommand.class);
        Map<String, PropertyValue> properties = unmarshalledCommand.getMessageProperties();
        assertNotNull("Properties should not be null", properties);
        assertFalse("Properties should not be empty", properties.isEmpty());
        assertEquals("Unexpected properties size", 2, properties.size());
        PropertyValue testProperty = properties.get("test");
        assertNotNull("Unexpected property test", testProperty);
        assertTrue("Unexpected property test", testProperty.getValue() instanceof Number);
        assertEquals("Unexpected property value", 1, ((Number)testProperty.getValue()).intValue());
        Object generatorObject = properties.get("generator");
        assertTrue("Unexpected generator object", generatorObject instanceof ListPropertyValue);
        PropertyValue generator = (PropertyValue)generatorObject;
        assertEquals("Unexpected generator value", "first", generator.getValue());
        for (int i = 0; i < 10; i++)
        {
            assertEquals("Unexpected generator value", new Integer(i), generator.getValue());
        }
        String newJson =_jsonHandler.marshall(unmarshalledCommand);
        final TestCommand newUnmarshalledCommand = _jsonHandler.unmarshall(newJson, TestCommand.class);
        assertEquals("Unmarshalled command should be equal to the original object", unmarshalledCommand, newUnmarshalledCommand);
    }

    /**
     * A {@link Command} designed to exercise {@link JsonHandler}, e.g does it handle a map of enums?.
     *
     * This class is non-private to avoid auto-deletion of "unused" fields/methods
     */
    static class SendChristmasCards extends Command
    {
        enum CardType {FUNNY, TRADITIONAL}

        private Map<CardType, Integer> _cardTypes;
        private List<Person> persons;

        public SendChristmasCards(final CommandType type, Map<CardType, Integer> cardTypes)
        {
            super(type);
            _cardTypes = cardTypes;
        }

        public Map<CardType, Integer> getCardTypes()
        {
            return _cardTypes;
        }

        public List<Person> getPersons()
        {
            return persons;
        }

        @Override
        public boolean equals(final Object obj)
        {
            return EqualsBuilder.reflectionEquals(this, obj);
        }
    }

    /**
     * This class is non-private to avoid auto-deletion of "unused" fields/methods
     */
    static class Person
    {
        private String _firstName;

        public Person(final String firstName)
        {
            _firstName = firstName;
        }

        public String getFirstName()
        {
            return _firstName;
        }

        @Override
        public boolean equals(final Object obj)
        {
            return EqualsBuilder.reflectionEquals(this, obj);
        }

    }

    /**
     * Yet another test class
     */
    static class TestCommand extends Command
    {

        private Map<String, PropertyValue> _messageProperties;

        public TestCommand(CommandType type)
        {
            super(type);
        }

        public Map<String, PropertyValue> getMessageProperties()
        {
            return _messageProperties;
        }

        public void setMessageProperties(Map<String, PropertyValue> _messageProperties)
        {
            this._messageProperties = _messageProperties;
        }

        @Override
        public boolean equals(final Object obj)
        {
            if (obj == null || !(obj instanceof TestCommand))
            {
                return false;
            }
            TestCommand other = (TestCommand)obj;
            if (_messageProperties == null && other._messageProperties != null )
            {
                return false;
            }
            return _messageProperties.equals(other._messageProperties);
        }
    }
}
