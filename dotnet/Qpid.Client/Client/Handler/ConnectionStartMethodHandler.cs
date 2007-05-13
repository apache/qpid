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
using System;
using System.Collections;
using System.Text;
using log4net;
using Qpid.Client.Protocol;
using Qpid.Client.Security;
using Qpid.Client.State;
using Qpid.Framing;
using Qpid.Sasl;


namespace Qpid.Client.Handler
{
    public class ConnectionStartMethodHandler : IStateAwareMethodListener
    {
        private static readonly ILog _log = LogManager.GetLogger(typeof(ConnectionStartMethodHandler));

        public void MethodReceived(AMQStateManager stateManager, AMQMethodEvent evt)
        {
            ConnectionStartBody body = (ConnectionStartBody) evt.Method;
            AMQProtocolSession ps = evt.ProtocolSession;

            try
            {
                if ( body.Mechanisms == null )
                {
                    throw new AMQException("mechanism not specified in ConnectionStart method frame");
                }
                string mechanisms = Encoding.UTF8.GetString(body.Mechanisms);
                string selectedMechanism = ChooseMechanism(mechanisms);
                if ( selectedMechanism == null )
                {
                    throw new AMQException("No supported security mechanism found, passed: " + mechanisms);
                }
               
                byte[] saslResponse = DoAuthentication(selectedMechanism, ps);

                if (body.Locales == null)
                {
                    throw new AMQException("Locales is not defined in Connection Start method");
                }
                string allLocales = Encoding.ASCII.GetString(body.Locales);
                string[] locales = allLocales.Split(' ');
                string selectedLocale;
                if (locales != null && locales.Length > 0)
                {
                    selectedLocale = locales[0];
                }
                else
                {
                    throw new AMQException("No locales sent from server, passed: " + locales);
                }

                stateManager.ChangeState(AMQState.CONNECTION_NOT_TUNED);
                FieldTable clientProperties = new FieldTable();
                clientProperties["product"] = "Qpid.NET";
                clientProperties["version"] = "1.0";
                clientProperties["platform"] = GetFullSystemInfo();
                AMQFrame frame = ConnectionStartOkBody.CreateAMQFrame(
                   evt.ChannelId, clientProperties, selectedMechanism,
                  saslResponse, selectedLocale);
                ps.WriteFrame(frame);
            }
            catch (Exception e)
            {
                throw new AMQException(_log, "Unable to decode data: " + e, e);
            }
        }

        private string GetFullSystemInfo()
        {
            /*StringBuffer fullSystemInfo = new StringBuffer();
            fullSystemInfo.append(System.getProperty("java.runtime.name"));
            fullSystemInfo.append(", " + System.getProperty("java.runtime.version"));
            fullSystemInfo.append(", " + System.getProperty("java.vendor"));
            fullSystemInfo.append(", " + System.getProperty("os.arch"));
            fullSystemInfo.append(", " + System.getProperty("os.name"));
            fullSystemInfo.append(", " + System.getProperty("os.version"));
            fullSystemInfo.append(", " + System.getProperty("sun.os.patch.level"));*/
            // TODO: add in details here
            return ".NET 1.1 Client";
        }

       private string ChooseMechanism(string mechanisms)
       {
          return CallbackHandlerRegistry.Instance.ChooseMechanism(mechanisms);
       }

       private byte[] DoAuthentication(string selectedMechanism, AMQProtocolSession ps)
       {
           ISaslClient saslClient = Sasl.Sasl.CreateClient(
               new string[] { selectedMechanism }, null, "AMQP", "localhost",
               new Hashtable(), CreateCallbackHandler(selectedMechanism, ps)
           );
           if ( saslClient == null )
           {
               throw new AMQException("Client SASL configuration error: no SaslClient could be created for mechanism " +
                                      selectedMechanism);
           }
           ps.SaslClient = saslClient;
           try
           {
               return saslClient.HasInitialResponse ?
                  saslClient.EvaluateChallenge(new byte[0]) : null;
           } catch ( Exception ex )
           {
               ps.SaslClient = null;
               throw new AMQException("Unable to create SASL client", ex);
           }
       }

       private IAMQCallbackHandler CreateCallbackHandler(string mechanism, AMQProtocolSession session)
       {
           Type type = CallbackHandlerRegistry.Instance.GetCallbackHandler(mechanism);
           IAMQCallbackHandler handler = 
               (IAMQCallbackHandler)Activator.CreateInstance(type);
           if ( handler == null )
               throw new AMQException("Unable to create callback handler: " + mechanism);
           handler.Initialize(session);
           return handler;
       }
    }
}
