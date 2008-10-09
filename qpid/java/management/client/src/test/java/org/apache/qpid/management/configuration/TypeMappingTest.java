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
package org.apache.qpid.management.configuration;

import org.apache.qpid.management.TestConstants;
import org.apache.qpid.management.domain.model.type.Uint8;

import junit.framework.TestCase;

/**
 * Test case for type mapping.
 * 
 * @author Andrea Gazzarini
 */
public class TypeMappingTest extends TestCase
{
    private TypeMapping _mapping;
    
    @Override
    protected void setUp () throws Exception
    {
        _mapping = new TypeMapping();
    }
    
    /**
     * Tests the execution of the setCode method when a valid code is given.
     * 
     * <br>precondition : given code is a valid number. 
     * <br>postcondition : no exception is thrown and the mapping contained the requested code.
     */
    public void testSetCodeOK() 
    {
        _mapping.setCode(String.valueOf(TestConstants.VALID_CODE));
        assertEquals(TestConstants.VALID_CODE,_mapping.getCode());
    }
    
    /**
     * Tests the execution of the setCode method when an invalid code is given.
     * 
     * <br>precondition : given code is an invalid number. 
     * <br>postcondition : an exception is thrown indicating the failure.
     */
    public void testSetCodeKO_withInvalidNumber ()
    {
        try {
            _mapping.setCode(String.valueOf(TestConstants.VALID_CODE)+"a");
            fail("The given string is not a number and therefore an exception must be thrown.");
        } catch(NumberFormatException expected) 
        {
            
        }
    }
    
    /**
     * Tests the execution of the setType() method when a valid type class is given.
     * 
     * <br>precondition : a valid class type is supplied.
     * <br>postcondition : no exception is thrown and the mapping contains the previously associated type.
     */
    public void testSetTypeOK() 
    {
        _mapping.setType(Uint8.class.getName());
        assertTrue(_mapping.getType() instanceof Uint8);
    }
    
    /**
     * Tests the execution of the setType() method when a invalid type class is given.
     * 
     * <br>precondition : an invalid class type is supplied.
     * <br>postcondition : an exception is thrown indicating the failure.
     */
    public void testSetTypeKO_withTypeClassNotFound() 
    {
        try 
        {
            _mapping.setType(Uint8.class.getName()+"a");
            fail("If the supplied class doesn't exist an exception must be thrown.");
        } catch(IllegalArgumentException expected) {
            assertTrue(expected.getCause() instanceof ClassNotFoundException);
        }
    }
    
    /**
     * Tests the execution of the setType() method when a invalid type class is given.
     * 
     * <br>precondition : an invalid class type is supplied (is not a Type).
     * <br>postcondition : an exception is thrown indicating the failure.
     */
    public void testSetTypeKO_withInvalidType() 
    {
        try 
        {
            _mapping.setType(String.class.getName());
            fail("If the supplied class is not a Type an exception must be thrown.");
        } catch(IllegalArgumentException expected) {
            assertTrue(expected.getCause() instanceof ClassCastException);
        }
    }    
}