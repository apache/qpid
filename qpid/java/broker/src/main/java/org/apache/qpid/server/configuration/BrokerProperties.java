package org.apache.qpid.server.configuration;

/**
 * Declares broker system property names
 */
public class BrokerProperties
{
    public static final String PROPERTY_DEAD_LETTER_EXCHANGE_SUFFIX = "qpid.dead_letter_exchange_suffix";
    public static final String PROPERTY_DEAD_LETTER_QUEUE_SUFFIX = "qpid.dead_letter_queue_suffix";
    public static final String PROPERTY_HOUSE_KEEPING_CHECK_PERIOD = "qpid.house_keeping_check_period";
    public static final String PROPERTY_MAXIMUM_MESSAGE_AGE = "qpid.maximum_message_age";
    public static final String PROPERTY_MAXIMUM_MESSAGE_COUNT = "qpid.maximum_message_count";
    public static final String PROPERTY_MAXIMUM_QUEUE_DEPTH = "qpid.maximum_queue_depth";
    public static final String PROPERTY_MAXIMUM_MESSAGE_SIZE = "qpid.maximum_message_size";
    public static final String PROPERTY_MAXIMUM_CHANNEL_COUNT = "qpid.maximum_channel_count";
    public static final String PROPERTY_MINIMUM_ALERT_REPEAT_GAP = "qpid.minimum_alert_repeat_gap";
    public static final String PROPERTY_FLOW_CAPACITY = "qpid.flow_capacity";
    public static final String PROPERTY_FLOW_RESUME_CAPACITY = "qpid.flow_resume_capacity";
    public static final String PROPERTY_FRAME_SIZE = "qpid.frame_size";
    public static final String PROPERTY_MSG_AUTH = "qpid.msg_auth";

    public static final long DEFAULT_MINIMUM_ALERT_REPEAT_GAP = 30000l;
    public static final long DEFAULT_HOUSEKEEPING_PERIOD = 30000L;

    public static final String PROPERTY_NO_STATUS_UPDATES = "qpid.no_status_updates";

    private BrokerProperties()
    {
    }
}
