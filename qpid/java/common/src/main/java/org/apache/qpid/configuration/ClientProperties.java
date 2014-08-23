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
 *
 * @see CommonProperties
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
     * Frequency of heartbeat messages (in milliseconds)
     * @see #QPID_HEARTBEAT_INTERVAL
     */
    @Deprecated
    public static final String IDLE_TIMEOUT_PROP_NAME = "idle_timeout";

    /**
     * Frequency of heartbeat messages (in seconds)
     * @see #QPID_HEARTBEAT_INTERVAL
     */
    @Deprecated
    public static final String AMQJ_HEARTBEAT_DELAY = "amqj.heartbeat.delay";

    /**
     * Frequency of heartbeat messages (in seconds)
     */
    public static final String QPID_HEARTBEAT_INTERVAL = "qpid.heartbeat";

    /**
     * Default heartbeat interval (used by 0-10 protocol).
     */
    public static final int QPID_HEARTBEAT_INTERVAL_010_DEFAULT = 120;

    /**
     * @see #QPID_HEARTBEAT_TIMEOUT_FACTOR
     */
    @Deprecated
    public static final String AMQJ_HEARTBEAT_TIMEOUT_FACTOR = "amqj.heartbeat.timeoutFactor";

    /**
     * The factor applied to {@link #QPID_HEARTBEAT_INTERVAL} that determines the maximum
     * length of time that may elapse before the peer is deemed to have failed.
     *
     * @see #QPID_HEARTBEAT_TIMEOUT_FACTOR_DEFAULT
     */
    public static final String QPID_HEARTBEAT_TIMEOUT_FACTOR = "qpid.heartbeat_timeout_factor";

    /**
     * Default heartbeat timeout factor.
     */
    public static final float QPID_HEARTBEAT_TIMEOUT_FACTOR_DEFAULT = 2.0f;

    /**
     * This value will be used to determine the default destination syntax type.
     * Currently the two types are Binding URL (java only) and the Addressing format (used by
     * all clients).
     */
    public static final String DEST_SYNTAX = "qpid.dest_syntax";

    public static final String USE_LEGACY_MAP_MESSAGE_FORMAT = "qpid.use_legacy_map_message";

    public static final String USE_LEGACY_STREAM_MESSAGE_FORMAT = "qpid.use_legacy_stream_message";

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

    /**
     * System property to set the time (in millis) to wait before failing when sending and
     * the client has been flow controlled by the broker.
     */
    public static final String QPID_FLOW_CONTROL_WAIT_FAILURE = "qpid.flow_control_wait_failure";

    /**
     * Default time (in millis) to wait before failing when sending and the client has been
     * flow controlled by the broker.
     */
    public static final long DEFAULT_FLOW_CONTROL_WAIT_FAILURE = 60000L;

    /**
     * System property to set the time (in millis) between log notifications that a
     * send is waiting because the client was flow controlled by the broker.
     */
    public static final String QPID_FLOW_CONTROL_WAIT_NOTIFY_PERIOD = "qpid.flow_control_wait_notify_period";

    /**
     * Default time (in millis) between log notifications that a send is
     * waiting because the client was flow controlled by the broker.
     */
    public static final long DEFAULT_FLOW_CONTROL_WAIT_NOTIFY_PERIOD = 5000L;

    /**
     * System property to control whether the client will declare queues during
     * consumer creation when using BindingURLs.
     */
    public static final String QPID_DECLARE_QUEUES_PROP_NAME = "qpid.declare_queues";

    /**
     * System property to control whether the client will declare exchanges during
     * producer/consumer creation when using BindingURLs.
     */
    public static final String QPID_DECLARE_EXCHANGES_PROP_NAME = "qpid.declare_exchanges";
    /**
     * System property to control whether the client will bind queues during
     * consumer creation when using BindingURLs.
     */
    public static final String QPID_BIND_QUEUES_PROP_NAME = "qpid.bind_queues";

    public static final String VERIFY_QUEUE_ON_SEND = "qpid.verify_queue_on_send";

    public static final String QPID_MAX_CACHED_ADDR_OPTION_STRINGS = "qpid.max_cached_address_option_strings";
    public static final int DEFAULT_MAX_CACHED_ADDR_OPTION_STRINGS = 10;

    /**
     * System property to control whether the 0-8/0-9/0-9-1 client will set the message
     * 'expiration' header using the computed expiration value (default, when false) or instead set
     * it to the raw TTL (when true). May be necessary for interop with other vendors.
     */
    public static final String SET_EXPIRATION_AS_TTL = "qpid.set_expiration_as_ttl";

    /**
     * System property to set a default value for a connection option 'ssl_verify_hostname'
     */
    public static final String CONNECTION_OPTION_SSL_VERIFY_HOST_NAME = "qpid.connection_ssl_verify_hostname";
    public static final boolean DEFAULT_CONNECTION_OPTION_SSL_VERIFY_HOST_NAME = true;

    /**
     * System property to set a default value for a connection option 'compress_messages'
     */
    public static final String CONNECTION_OPTION_COMPRESS_MESSAGES = "qpid.connection_compress_messages";
    public static final boolean DEFAULT_CONNECTION_OPTION_COMPRESS_MESSAGES = false;


    /**
     * System property to set a default value for a connection option 'message_compression_threshold_size'
     */
    public static final String CONNECTION_OPTION_MESSAGE_COMPRESSION_THRESHOLD_SIZE = "qpid.message_compression_threshold_size";
    public static final int DEFAULT_MESSAGE_COMPRESSION_THRESHOLD_SIZE = 102400;

    public static final String ADDR_SYNTAX_SUPPORTED_IN_0_8 = "qpid.addr_syntax_supported";
    public static final boolean DEFAULT_ADDR_SYNTAX_0_8_SUPPORT = true;

    private ClientProperties()
    {
        //No instances
    }
}
