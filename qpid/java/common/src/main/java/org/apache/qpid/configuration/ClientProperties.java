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

package org.apache.qpid.configuration;

/**
 * This class centralized the Qpid client properties.
 */
public class ClientProperties
{

    /**
     * Currently with Qpid it is not possible to change the client ID.
     * If one is not specified upon connection construction, an id is generated automatically.
     * Therefore an exception is always thrown unless this property is set to true.
     * type: boolean
     */
    public static final String IGNORE_SET_CLIENTID_PROP_NAME = "ignore_setclientID";

    /**
     * This property is currently used within the 0.10 code path only
     * The maximum number of pre-fetched messages per destination
     * This property is used for all the connection unless it is overwritten by the connectionURL
     * type: long
     */
    public static final String MAX_PREFETCH_PROP_NAME = "max_prefetch";
    public static final String MAX_PREFETCH_DEFAULT = "500";

    /**
     * When true a sync command is sent after every persistent messages.
     * type: boolean
     */
    public static final String SYNC_PERSISTENT_PROP_NAME = "sync_persistence";

    /**
     * When true a sync command is sent after sending a message ack.
     * type: boolean
     */
    public static final String SYNC_ACK_PROP_NAME = "sync_ack";

    /**
     * sync_publish property - {persistent|all}
     * If set to 'persistent',then persistent messages will be publish synchronously
     * If set to 'all', then all messages regardless of the delivery mode will be
     * published synchronously.
     */
    public static final String SYNC_PUBLISH_PROP_NAME = "sync_publish";

    /**
     * This value will be used in the following settings
     * To calculate the SO_TIMEOUT option of the socket (2*idle_timeout)
     * If this values is between the max and min values specified for heartbeat
     * by the broker in TuneOK it will be used as the heartbeat interval.
     * If not a warning will be printed and the max value specified for
     * heartbeat in TuneOK will be used
     *
     * The default idle timeout is set to 120 secs
     */
    public static final String IDLE_TIMEOUT_PROP_NAME = "idle_timeout";
    public static final long DEFAULT_IDLE_TIMEOUT = 120000;

    public static final String HEARTBEAT = "qpid.heartbeat";
    public static final int HEARTBEAT_DEFAULT = 120;

    /**
     * This value will be used to determine the default destination syntax type.
     * Currently the two types are Binding URL (java only) and the Addressing format (used by
     * all clients).
     */
    public static final String DEST_SYNTAX = "qpid.dest_syntax";

    public static final String USE_LEGACY_MAP_MESSAGE_FORMAT = "qpid.use_legacy_map_message";

    public static final String AMQP_VERSION = "qpid.amqp.version";

    public static final String QPID_VERIFY_CLIENT_ID = "qpid.verify_client_id";

    /**
     * System properties to change the default timeout used during
     * synchronous operations.
     */
    public static final String QPID_SYNC_OP_TIMEOUT = "qpid.sync_op_timeout";
    @Deprecated
    public static final String AMQJ_DEFAULT_SYNCWRITE_TIMEOUT = "amqj.default_syncwrite_timeout";

    /**
     * A default timeout value for synchronous operations
     */
    public static final int DEFAULT_SYNC_OPERATION_TIMEOUT = 60000;

    /**
     * System properties to change the default value used for TCP_NODELAY
     */
    public static final String QPID_TCP_NODELAY_PROP_NAME = "qpid.tcp_nodelay";
    @Deprecated
    public static final String AMQJ_TCP_NODELAY_PROP_NAME = "amqj.tcp_nodelay";

    /**
     * System property to set the reject behaviour. default value will be 'normal' but can be
     * changed to 'server' in which case the server decides whether a message should be requeued
     * or dead lettered.
     * This can be overridden by the more specific settings at connection or binding URL level.
     */
    public static final String REJECT_BEHAVIOUR_PROP_NAME = "qpid.reject.behaviour";

    private ClientProperties()
    {
    }

    /**
     * System property used to set the key manager factory algorithm.
     *
     * Historically, Qpid referred to this as {@value #QPID_SSL_KEY_STORE_CERT_TYPE_PROP_NAME}.
     */
    public static final String QPID_SSL_KEY_MANAGER_FACTORY_ALGORITHM_PROP_NAME = "qpid.ssl.KeyManagerFactory.algorithm";
    @Deprecated
    public static final String QPID_SSL_KEY_STORE_CERT_TYPE_PROP_NAME = "qpid.ssl.keyStoreCertType";

    /**
     * System property used to set the trust manager factory algorithm.
     *
     * Historically, Qpid referred to this as {@value #QPID_SSL_TRUST_STORE_CERT_TYPE_PROP_NAME}.
     */
    public static final String QPID_SSL_TRUST_MANAGER_FACTORY_ALGORITHM_PROP_NAME = "qpid.ssl.TrustManagerFactory.algorithm";
    @Deprecated
    public static final String QPID_SSL_TRUST_STORE_CERT_TYPE_PROP_NAME = "qpid.ssl.trustStoreCertType";

    /**
     * System property to enable allow dispatcher thread to be run as a daemon thread
     */
    public static final String DAEMON_DISPATCHER = "qpid.jms.daemon.dispatcher";

    /**
     * Used to name the process utilising the Qpid client, to override the default
     * value is used in the ConnectionStartOk reply to the broker.
     */
    public static final String PROCESS_NAME = "qpid.client_process";

    /**
     * System property used to set the socket receive buffer size.
     *
     * Historically, Qpid referred to this as {@value #LEGACY_RECEIVE_BUFFER_SIZE_PROP_NAME}.
     */
    public static final String RECEIVE_BUFFER_SIZE_PROP_NAME  = "qpid.receive_buffer_size";
    @Deprecated
    public static final String LEGACY_RECEIVE_BUFFER_SIZE_PROP_NAME  = "amqj.receiveBufferSize";

    /**
     * System property used to set the socket send buffer size.
     *
     * Historically, Qpid referred to this as {@value #LEGACY_SEND_BUFFER_SIZE_PROP_NAME}.
     */
    public static final String SEND_BUFFER_SIZE_PROP_NAME  = "qpid.send_buffer_size";
    @Deprecated
    public static final String LEGACY_SEND_BUFFER_SIZE_PROP_NAME  = "amqj.sendBufferSize";
}
