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
package org.apache.qpid.nclient.jms;

import org.apache.qpid.nclient.api.ExceptionListener;
import org.apache.qpid.nclient.exception.QpidException;

import javax.jms.JMSException;

/**
 * Created by Arnaud Simon
 * Date: 25-Jul-2007
 * Time: 12:08:47
 */
public class QpidExceptionListenerImpl implements ExceptionListener
{
    private javax.jms.ExceptionListener _jmsExceptionListener;

    public QpidExceptionListenerImpl()
    {
    }

    void setJMSExceptionListner(javax.jms.ExceptionListener jmsExceptionListener)
    {
       _jmsExceptionListener = jmsExceptionListener;
    }
    //----- ExceptionListener API

    public void onException(QpidException exception)
    {
        // convert this exception in a JMS exception
        JMSException jmsException = ExceptionHelper.convertQpidExceptionToJMSException(exception);
        // propagate to the jms exception listener
        if( _jmsExceptionListener != null )
        {
            _jmsExceptionListener.onException(jmsException);
        }
    }
}
