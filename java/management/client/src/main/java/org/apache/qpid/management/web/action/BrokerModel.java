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
package org.apache.qpid.management.web.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.ObjectName;

import org.apache.qpid.management.Names;

public class BrokerModel
{
	private Map<String, List<ObjectName>> objectsByType = new HashMap<String, List<ObjectName>>();
	
	private String id;
	
	void addObject(ObjectName name)
	{
		String packageName = name.getKeyProperty(Names.PACKAGE);
		String className = name.getKeyProperty(Names.CLASS);
		if (className != null)
		{
			String fqn = packageName+"."+className;
			
			List<ObjectName> objects = objectsByType.get(fqn);
			if (objects == null)
			{
				objects = new ArrayList<ObjectName>();
				objectsByType.put(fqn,objects);
			}
			objects.add(name);		
		}
	}

	public String getId()
	{
		return id;
	}

	public void setId(String id)
	{
		this.id = id;
	}
	
	public Set<String> getCategoryNames(){
		return objectsByType.keySet();
	}
	
	public List<ObjectName> getCategory(String name) 
	{
		return objectsByType.get(name);
	}
	
	public int getCategoryCount()
	{
		return objectsByType.keySet().size();
	}
}