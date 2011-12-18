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
package org.apache.qpid.jca.example.client;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.spi.NamingManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QpidTestUtil {
    private static final Logger _log = LoggerFactory.getLogger(QpidTestUtil.class);

	/*
	 * Encapsulate looking up in JNDI and working around a seeming bug in OpenEJB which returns a
	 * Reference when it should just return an object constructed from it
	 */
	static Object getFromJNDI(Context context, String name) throws NamingException, Exception
	{
		Object o = context.lookup(name);
		if (o instanceof Reference)
		{
			_log.debug("Got a Reference back from JNDI for " + name + " - working around");
			return NamingManager.getObjectInstance(o, null, null, null);
		}
		else
		{
			return o;
		}
	}

}
