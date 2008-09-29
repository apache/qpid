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
import org.apache.qpid.management.domain.model.AccessMode;

import junit.framework.TestCase;

/**
 * Test case for AccessMode mapping.
 * 
 * @author Andrea Gazzarini
 */
public class AccessModeMappingTest extends TestCase
{
    private AccessModeMapping _mapping;
        
    /**
     * Set up fixture for this test case.
     */
    @Override
    protected void setUp () throws Exception
    {
        _mapping = new AccessModeMapping();
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
        } catch(NumberFormatException expected) {
            
        }
    }

    /**
     * Tests the execution of the setAccessMode method when a valid access code is given.
     * 
     * <br>precondition : given code is valid (i.e. RW, RC or RO). 
     * <br>postcondition : no exception is thrown and the mapping contained the requested access mode.
     */
    public void testSetAccessModeOK() 
    {
        _mapping.setAccessMode("RW");
        assertEquals(AccessMode.RW,_mapping.getAccessMode());        

        _mapping.setAccessMode("RC");
        assertEquals(AccessMode.RC,_mapping.getAccessMode());        

        _mapping.setAccessMode("RO");
        assertEquals(AccessMode.RO,_mapping.getAccessMode());        
    }
    
    /**
     * Tests the execution of the setAccessMode method when an unknown code is given.
     * 
     * <br>precondition : given code is an unknown code. 
     * <br>postcondition : an exception is thrown indicating the failure.
     */
    public void testSetAccessModeKO ()
    {
        try {
            _mapping.setAccessMode(AccessMode.RW.toString()+"X");
            fail("The given string is not a string representation of a valid access mode.");
        } catch(IllegalArgumentException expected) {
            
        }
    }
}