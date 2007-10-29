/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.requestreply;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;

/**
 *
 *
 */
public class InitialContextHelper
{

    public static Context getInitialContext(String propertyFile) throws IOException, NamingException
    {
            if ((propertyFile == null) || (propertyFile.length() == 0))
            {
                propertyFile = "/perftests.properties";
            }

            Properties fileProperties = new Properties();
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            // NB: Need to change path to reflect package if moving classes around !
            InputStream is = cl.getResourceAsStream(propertyFile);
            if( is != null )
            {
            fileProperties.load(is);
            return new InitialContext(fileProperties);
            }
        else
            {
                return new InitialContext();
            }
    }
}
