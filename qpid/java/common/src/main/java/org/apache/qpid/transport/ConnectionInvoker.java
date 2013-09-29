package org.apache.qpid.transport;
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


import java.util.Map;

public abstract class ConnectionInvoker {

    final void connectionStart(Map<String,Object> serverProperties, java.util.List<Object> mechanisms, java.util.List<Object> locales, Option ... _options) {
        invoke(new ConnectionStart(serverProperties, mechanisms, locales, _options));
    }

    final void connectionStartOk(Map<String,Object> clientProperties, String mechanism, byte[] response, String locale, Option ... _options) {
        invoke(new ConnectionStartOk(clientProperties, mechanism, response, locale, _options));
    }

    final void connectionSecure(byte[] challenge, Option ... _options) {
        invoke(new ConnectionSecure(challenge, _options));
    }

    final void connectionSecureOk(byte[] response, Option ... _options) {
        invoke(new ConnectionSecureOk(response, _options));
    }

    final void connectionTune(int channelMax, int maxFrameSize, int heartbeatMin, int heartbeatMax, Option ... _options) {
        invoke(new ConnectionTune(channelMax, maxFrameSize, heartbeatMin, heartbeatMax, _options));
    }

    final void connectionTuneOk(int channelMax, int maxFrameSize, int heartbeat, Option ... _options) {
        invoke(new ConnectionTuneOk(channelMax, maxFrameSize, heartbeat, _options));
    }

    final void connectionOpen(String virtualHost, java.util.List<Object> capabilities, Option ... _options) {
        invoke(new ConnectionOpen(virtualHost, capabilities, _options));
    }

    final void connectionOpenOk(java.util.List<Object> knownHosts, Option ... _options) {
        invoke(new ConnectionOpenOk(knownHosts, _options));
    }

    final void connectionRedirect(String host, java.util.List<Object> knownHosts, Option ... _options) {
        invoke(new ConnectionRedirect(host, knownHosts, _options));
    }

    final void connectionHeartbeat(Option ... _options) {
        invoke(new ConnectionHeartbeat(_options));
    }

    final void connectionClose(ConnectionCloseCode replyCode, String replyText, Option ... _options) {
        invoke(new ConnectionClose(replyCode, replyText, _options));
    }

    final void connectionCloseOk(Option ... _options) {
        invoke(new ConnectionCloseOk(_options));
    }

    protected abstract void invoke(Method method);

}
