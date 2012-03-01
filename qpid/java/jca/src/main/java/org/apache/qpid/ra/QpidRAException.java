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

package org.apache.qpid.ra;


/**
 * Qpid Resource Adapter exception.
 */
public class QpidRAException extends Exception
{
   /**
    * The serial version uid for this serializable class.
    */
   private static final long serialVersionUID = 2921345326731695238L;

   /**
    * Create a default Qpid ra exception.
    */
   public QpidRAException()
   {
      super();
   }

   /**
    * Create an Qpid ra exception with a specific message.
    * @param message The message associated with this exception.
    */
   public QpidRAException(final String message)
   {
      super(message);
   }

   /**
    * Create an Qpid ra exception with a specific cause.
    * @param cause The cause associated with this exception.
    */
   public QpidRAException(final Throwable cause)
   {
      super(cause);
   }

   /**
    * Create an Qpid ra exception with a specific message and cause.
    * @param message The message associated with this exception.
    * @param cause The cause associated with this exception.
    */
   public QpidRAException(final String message, final Throwable cause)
   {
      super(message, cause);
   }
}
