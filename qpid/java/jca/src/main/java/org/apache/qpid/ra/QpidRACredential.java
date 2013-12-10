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

import java.io.Serializable;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Set;

import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.SecurityException;
import javax.resource.spi.security.PasswordCredential;
import javax.security.auth.Subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Credential information
 *
 */
public class QpidRACredential implements Serializable
{
   /** Serial version UID */
   private static final long serialVersionUID = 7040664839205409352L;

   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRACredential.class);

   /** The user name */
   private String _userName;

   /** The password */
   private String _password;

   /**
    * Private constructor
    */
   private QpidRACredential()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor()");
      }
   }

   /**
    * Get the user name
    * @return The value
    */
   public String getUserName()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getUserName()");
      }

      return _userName;
   }

   /**
    * Set the user name
    * @param userName The value
    */
   private void setUserName(final String userName)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setUserName(" + userName + ")");
      }

      this._userName = userName;
   }

   /**
    * Get the password
    * @return The value
    */
   public String getPassword()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getPassword()");
      }

      return _password;
   }

   /**
    * Set the password
    * @param password The value
    */
   private void setPassword(final String password)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setPassword(****)");
      }

      this._password = password;
   }

   /**
    * Get credentials
    * @param mcf The managed connection factory
    * @param subject The subject
    * @param info The connection request info
    * @return The credentials
    * @exception SecurityException Thrown if the credentials cant be retrieved
    */
   public static QpidRACredential getCredential(final ManagedConnectionFactory mcf,
                                                   final Subject subject,
                                                   final ConnectionRequestInfo info) throws SecurityException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getCredential(" + mcf + ", " + subject + ", " + info + ")");
      }

      QpidRACredential jc = new QpidRACredential();
      if (subject == null && info != null)
      {
         jc.setUserName(((QpidRAConnectionRequestInfo)info).getUserName());
         jc.setPassword(((QpidRAConnectionRequestInfo)info).getPassword());
      }
      else if (subject != null)
      {
         PasswordCredential pwdc = GetCredentialAction.getCredential(subject, mcf);

         if (pwdc == null)
         {
            throw new SecurityException("No password credentials found");
         }

         jc.setUserName(pwdc.getUserName());
         jc.setPassword(new String(pwdc.getPassword()));
      }
      else
      {
         throw new SecurityException("No Subject or ConnectionRequestInfo set, could not get credentials");
      }

      return jc;
   }

   /**
    * String representation
    * @return The representation
    */
   @Override
   public String toString()
   {
      return super.toString() + "{ username=" + _userName + ", password=**** }";
   }

   /**
    * Privileged class to get credentials
    */
   private static class GetCredentialAction implements PrivilegedAction<PasswordCredential>
   {
      /** The subject */
      private final Subject subject;

      /** The managed connection factory */
      private final ManagedConnectionFactory mcf;

      /**
       * Constructor
       * @param subject The subject
       * @param mcf The managed connection factory
       */
      GetCredentialAction(final Subject subject, final ManagedConnectionFactory mcf)
      {
         if (_log.isTraceEnabled())
         {
            _log.trace("constructor(" + subject + ", " + mcf + ")");
         }

         this.subject = subject;
         this.mcf = mcf;
      }

      /**
       * Run
       * @return The credential
       */
      public PasswordCredential run()
      {
         if (_log.isTraceEnabled())
         {
            _log.trace("run()");
         }

         Set<PasswordCredential> creds = subject.getPrivateCredentials(PasswordCredential.class);
         PasswordCredential pwdc = null;

         for (PasswordCredential curCred : creds)
         {
            if (curCred.getManagedConnectionFactory().equals(mcf))
            {
               pwdc = curCred;
               break;
            }
         }
         return pwdc;
      }

      /**
       * Get credentials
       * @param subject The subject
       * @param mcf The managed connection factory
       * @return The credential
       */
      static PasswordCredential getCredential(final Subject subject, final ManagedConnectionFactory mcf)
      {
         if (_log.isTraceEnabled())
         {
            _log.trace("getCredential(" + subject + ", " + mcf + ")");
         }

         GetCredentialAction action = new GetCredentialAction(subject, mcf);
         return AccessController.doPrivileged(action);
      }
   }
}
