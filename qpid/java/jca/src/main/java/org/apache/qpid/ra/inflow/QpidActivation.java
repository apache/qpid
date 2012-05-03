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
package org.apache.qpid.ra.inflow;

import java.util.ArrayList;
import java.util.List;

import javax.resource.ResourceException;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.Work;

import org.apache.qpid.ra.QpidResourceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The activation.
 *
 */
public class QpidActivation extends QpidExceptionHandler
{
   private static final Logger _log = LoggerFactory.getLogger(QpidActivation.class);

   private final List<QpidMessageHandler> _handlers = new ArrayList<QpidMessageHandler>();

   
   /**
    * Constructor
    *
    * @param ra              The resource adapter
    * @param endpointFactory The endpoint factory
    * @param spec            The activation spec
    * @throws ResourceException Thrown if an error occurs
    */
   public QpidActivation(final QpidResourceAdapter ra,
                            final MessageEndpointFactory endpointFactory,
                            final QpidActivationSpec spec) throws ResourceException
   {
      super(ra, spec, endpointFactory);
      
   }

   /**
    * Start the activation
    *
    * @throws ResourceException Thrown if an error occurs
    */
   public void start() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("start()");
      }
      _deliveryActive.set(true);
      new Thread(new SetupActivation()).start();
   }

   /**
    * Stop the activation
    */
   public void stop()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("stop()");
      }

      _deliveryActive.set(false);
      teardown();
   }

   /**
    * Setup the activation
    *
    * @throws Exception Thrown if an error occurs
    */
   public synchronized void setup() throws Exception
   {
      _log.debug("Setting up " + _spec);
      setupCF();      
      setupDestination();
      
      if(!_spec.isUseConnectionPerHandler())
      {
          setupConnection();
          _connection.setExceptionListener(this);          
      }
      
      for (int i = 0; i < _spec.getMaxSession(); i++)
      {
          try
          {
              QpidMessageHandler handler = null;
              
              if(_spec.isUseConnectionPerHandler())
              {
                  handler = new QpidMessageHandler(_ra, _spec, _endpointFactory, _ra.getTM());
              }
              else
              {
                  handler = new QpidMessageHandler(_ra, _spec, _endpointFactory, _ra.getTM(), _connection);
              }
              
              handler.start();
              _handlers.add(handler);
          }
          catch(Exception e)
          {
              try
              {
                  if(_connection != null)
                  {
                      this._connection.close();                      
                  }
              }
              catch (Exception e2)
              {
                 _log.trace("Ignored error closing connection", e2);
              }

              throw e;

          }         
        
      }

      if(!_spec.isUseConnectionPerHandler())
      {
          this._connection.start();
          _activated.set(true);
      }

      _log.debug("Setup complete " + this);
   }

   /**
    * Teardown the activation
    */
   protected synchronized void teardown()
   {
      _log.debug("Tearing down " + _spec);
      
      super.teardown();

      for (QpidMessageHandler handler : _handlers)
      {
         handler.stop();
      }

      _log.debug("Tearing down complete " + this);
   }

   /**
    * Get a string representation
    *
    * @return The value
    */
   @Override
   public String toString()
   {
      StringBuffer buffer = new StringBuffer();
      buffer.append(QpidActivation.class.getName()).append('(');
      buffer.append("spec=").append(_spec.getClass().getName());
      buffer.append(" mepf=").append(_endpointFactory.getClass().getName());
      buffer.append(" active=").append(_deliveryActive.get());
      if (_spec.getDestination() != null)
      {
         buffer.append(" destination=").append(_spec.getDestination());
      }
      buffer.append(" transacted=").append(_isDeliveryTransacted);
      buffer.append(')');
      return buffer.toString();
   }

    
   /**
    * Handles the setup
    */
   private class SetupActivation implements Work
   {
      public void run()
      {
         try
         {
            setup();
         }
         catch (Throwable t)
         {
            handleFailure(t);
         }
      }

      public void release()
      {
      }
   }
}
