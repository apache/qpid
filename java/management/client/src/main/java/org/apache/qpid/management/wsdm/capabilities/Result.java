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
package org.apache.qpid.management.wsdm.capabilities;

import java.util.*;

/**
 * Data Transfer Object that encapsulates the result of a method invocation.
 * This is the object that will be marshalled in XML and will contain the result of a method 
 * invocation (status code & text).
 * 
 * @author Andrea Gazzarini
 */
public class Result
{
	private long _statusCode;
	private String _statusText;
	private Map<String,Object> _outputParameters;
	
	/**
	 * Builds a new result DTO with the given parameters.
	 * 
	 * @param statusCode the return code.
	 * @param statusText the status message.
	 * @param outputParameters the output parameters.
	 */
	public Result(long statusCode, String statusText,Map<String, Object> outputParameters)
	{
		this._statusCode = statusCode;
		this._statusText = statusText;
		this._outputParameters = outputParameters;
	}
	
	/**
	 * Returns the status code.
	 * 
	 * @return the status code.
	 */
	public long getStatusCode()
	{
		return _statusCode;
	}
	
	/**
	 * Sets the status code.
	 * 
	 * @param statusCode the status code.
	 */
	void setStatusCode(long statusCode)
	{
		this._statusCode = statusCode;
	}
	
	/**
	 * Returns the status text.
	 * 
	 * @return the status text.
	 */
	public String getStatusText()
	{
		return _statusText;
	}
	
	/**
	 * Sets the status text.
	 * 
	 * @param statusText the status text.
	 */
	void setStatusText(String statusText)
	{
		this._statusText = statusText;
	}
	
	/**
	 * Returns the output parameterss.
	 * 
	 * @return the output parameterss.
	 */
	public Map<String, Object> getOutputParameters()
	{
		return _outputParameters;
	}
	
	/**
	 * Sets the output parameters.
	 * 
	 * @param outputParameters the output parameters.
	 */
	void setOutputParameters(Map<String, Object> outputParameters)
	{
		this._outputParameters = outputParameters;
	}
}
