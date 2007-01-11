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
package org.apache.qpid.framing;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;

public class RequestResponseMappingException extends AMQException
{
	private long requestResponseId;
    
    public RequestResponseMappingException(String msg)
    {
    	super(msg);
    }
    
    public RequestResponseMappingException(long requestResponseId, String msg)
    {
    	super(msg);
        this.requestResponseId = requestResponseId;
    }
    
    public RequestResponseMappingException(String msg, Throwable t)
    {
    	super(msg, t);
    }
    
    public RequestResponseMappingException(long requestResponseId, String msg, Throwable t)
    {
    	super(msg, t);
        this.requestResponseId = requestResponseId;
    }
    
    public RequestResponseMappingException(Logger logger, String msg)
    {
    	super(msg);
        logger.error(getMessage(), this);
    }
    
    public RequestResponseMappingException(Logger logger, long requestResponseId, String msg)
    {
    	super(msg);
        this.requestResponseId = requestResponseId;
        logger.error(getMessage(), this);
    }
    
    public RequestResponseMappingException(Logger logger, String msg, Throwable t)
    {
    	super(msg, t);
        logger.error(getMessage(), this);
    }
    
    public RequestResponseMappingException(Logger logger, long requestResponseId, String msg, Throwable t)
    {
    	super(msg, t);
        this.requestResponseId = requestResponseId;
        logger.error(getMessage(), this);
    }
    
    public long getRequestResponseId()
    {
    	return requestResponseId;
    }
}
