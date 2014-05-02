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
package org.apache.qpid.qmf2.console;

import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfException;

/**
 * The value(s) returned to the Console when the method call completes are represented by the MethodResult class.
 * <p>
 * The MethodResult class indicates whether the method call succeeded or not, and, on success, provides access to all
 * data returned by the method call.
 * <p>
 * Returned data is provided in QmfData map indexed by the name of the parameter. The QmfData map contains only those 
 * parameters that are classified as "output" by the SchemaMethod.
 * <p?
 * Should a method call result in a failure, this failure is indicated by the presence of an error object in
 * the MethodResult. This object is represented by a QmfException object, which contains a description of the
 * reason for the failure. There are no returned parameters when a method call fails.
 * <p>
 * Although not part of the QMF2 API I've made MethodResult extend QmfData so we can directly access the argument
 * or exception values of the MethodResult object, which tends to neaten up client code.
 *
 * @author Fraser Adams
 */
public final class MethodResult extends QmfData
{
    private QmfData _arguments = null;
    private QmfData _exception = null;

    /**
     * The main constructor, taking a java.util.Map as a parameter. In essence it "deserialises" its state from the Map.
     *
     * @param m the map used to construct the MethodResult.
     */
    @SuppressWarnings("unchecked")
    public MethodResult(final Map m)
    {
        super(m);
        _exception = this;
        String opcode = (m == null || !m.containsKey("qmf.opcode")) ? "none" : (String)m.get("qmf.opcode");
        if (m.size() == 0)
        { // Valid response from a method returning void
            _values = m;
            _arguments = this;
            _exception = null;
        }
        else if (opcode.equals("_method_response"))
        {
            Map args = (Map)m.get("_arguments");
            if (args != null)
            {
                _values = args;
                _arguments = this;
                _exception = null;
            }
        }
        else if (!opcode.equals("_exception"))
        {
            setValue("error_text", "Invalid response received, opcode: " + opcode);
        }
    }

    /**
     * Return true if the method call executed without error.
     * @return true if the method call executed without error.
     */
    public boolean succeeded()
    {
        return (_exception == null);
    }

    /**
     * Return the QmfData error object if method fails, else null.
     * @return the QmfData error object if method fails, else null.
     */
    public QmfData getException()
    {
        return _exception;
    }

    /**
     * Return a map of "name"=&lt;value&gt; pairs of all returned arguments.
     * @return a map of "name"=&lt;value&gt; pairs of all returned arguments.
     */
    public QmfData getArguments()
    {
        return _arguments;
    }

    /**
     * Return value of argument named "name".
     * @return value of argument named "name".
     */
    public Object getArgument(final String name)
    {
        if (_arguments == this)
        {
            return getValue(name);
        }
        return null;
    }

    /**
     * Return a QmfException object.
     * @return a QmfException object.
     * <p>
     * If the QmfData exception object contains a String property "error_text" or "message" return a QmfException object 
     * who's message is set to this value else return null;
     */
    public QmfException getQmfException()
    {
        if (_exception == this)
        {
            if (hasValue("error_text"))
            {
                return new QmfException(getStringValue("error_text"));
            }

            if (hasValue("message"))
            {
                return new QmfException(getStringValue("message"));
            }
        }
        return null;
    }
}



