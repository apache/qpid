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

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

/**
 *
 * A ConnectionFactoryObjectFactory.
 *
 * Given a reference - reconstructs a QpidRAConnectionFactory
 *
 */
public class ConnectionFactoryObjectFactory implements ObjectFactory
{
   static final String QPID_CF = "QPID-CF";

   public Object getObjectInstance(final Object ref, final Name name, final Context ctx, final Hashtable<?,?> props) throws Exception
   {
      if (!(ref instanceof Reference))
      {
          throw new IllegalArgumentException();
      }

      RefAddr ra = ((Reference)ref).get(QPID_CF);
      if (ra == null)
      {
          throw new NameNotFoundException();
      }

      byte[] bytes = (byte[])ra.getContent();

      return Util.deserialize(bytes);

   }
}
