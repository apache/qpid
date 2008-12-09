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

package org.apache.qpid.server.security.access.management;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.qpid.server.security.auth.database.Base64MD5PasswordFilePrincipalDatabase;

import junit.framework.TestCase;

public class AMQUserManagementMBeanTest extends TestCase {

	private Base64MD5PasswordFilePrincipalDatabase _database;
	private AMQUserManagementMBean _amqumMBean;

	private static final String _QPID_HOME =  System.getProperty("QPID_HOME");
	
	private static final String USERNAME = "testuser";
	private static final String PASSWORD = "password";
	private static final String JMXRIGHTS = "admin";
	private static final String TEMP_PASSWORD_FILE_NAME = "tempPasswordFile.tmp";
	private static final String TEMP_JMXACCESS_FILE_NAME = "tempJMXAccessFile.tmp";

	@Override
	protected void setUp() throws Exception {

		assertNotNull("QPID_HOME not set", _QPID_HOME);

		_database = new Base64MD5PasswordFilePrincipalDatabase();
		_amqumMBean = new AMQUserManagementMBean();
	}
	
	@Override
	protected void tearDown() throws Exception {
		
		File testFile = new File(_QPID_HOME + File.separator + TEMP_JMXACCESS_FILE_NAME + ".tmp");
		if (testFile.exists())
		{
			testFile.delete();
		}
		
		testFile = new File(_QPID_HOME + File.separator + TEMP_JMXACCESS_FILE_NAME + ".old");
		if (testFile.exists())
		{
			testFile.delete();
		}

		testFile = new File(_QPID_HOME + File.separator + TEMP_PASSWORD_FILE_NAME + ".tmp");
		if (testFile.exists())
		{
			testFile.delete();
		}
		
		testFile = new File(_QPID_HOME + File.separator + TEMP_PASSWORD_FILE_NAME + ".old");
		if (testFile.exists())
		{
			testFile.delete();
		}
	}

	public void testDeleteUser() {
		
		loadTestPasswordFile();
		loadTestAccessFile();
		
		boolean deleted = false;
		
		try{
		    deleted = _amqumMBean.deleteUser(USERNAME);
		}
		catch(Exception e){
			fail("Unable to delete user: " + e.getMessage());
		}
		
		assertTrue(deleted);
	}
	
	
	
	// ============================ Utility methods =========================
	
	private void loadTestPasswordFile()
    {
		try{
			File tempPasswordFile = new File(_QPID_HOME + File.separator + TEMP_PASSWORD_FILE_NAME);
			if (tempPasswordFile.exists())
			{
				tempPasswordFile.delete();
			}
			tempPasswordFile.deleteOnExit();

			BufferedWriter passwordWriter = new BufferedWriter(new FileWriter(tempPasswordFile));
			passwordWriter.write(USERNAME + ":" + PASSWORD);
			passwordWriter.newLine();
			passwordWriter.flush();


			_database.setPasswordFile(tempPasswordFile.toString());
			_amqumMBean.setPrincipalDatabase(_database);
		}
		catch (IOException e)
		{
			fail("Unable to create test password file: " + e.getMessage());
		}
    }

	private void loadTestAccessFile()
    {
		try{
			File tempAccessFile = new File(_QPID_HOME + File.separator + TEMP_JMXACCESS_FILE_NAME);
			if (tempAccessFile.exists())
			{
				tempAccessFile.delete();
			}
			tempAccessFile.deleteOnExit();

			BufferedWriter accessWriter = new BufferedWriter(new FileWriter(tempAccessFile));
			accessWriter.write(USERNAME + "=" + JMXRIGHTS);
			accessWriter.newLine();
			accessWriter.flush();


			_amqumMBean.setAccessFile(tempAccessFile.toString());
		}	
		catch (Exception e)
		{
			fail("Unable to create test access file: " + e.getMessage());
		}
    }
	
}
