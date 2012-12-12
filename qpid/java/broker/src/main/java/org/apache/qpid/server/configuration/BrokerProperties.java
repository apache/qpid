package org.apache.qpid.server.configuration;

import java.util.Locale;

/**
 * Declares broker system property names
 */
public class BrokerProperties
{
    public static final long DEFAULT_MINIMUM_ALERT_REPEAT_GAP = 30000l;
    public static final long DEFAULT_HOUSEKEEPING_PERIOD = 30000L;
    public static final int  DEFAULT_HEART_BEAT_DELAY = 0;
    public static final int  DEFAULT_HEART_BEAT_TIMEOUT_FACTOR = 2;
    public static final int  DEFAULT_STATISTICS_REPORTING_PERIOD = 0;

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
    public static final String PROPERTY_STATUS_UPDATES = "qpid.status_updates";
    public static final String PROPERTY_LOCALE = "qpid.locale";
    public static final String PROPERTY_DEFAULT_SUPPORTED_PROTOCOL_REPLY = "qpid.default_supported_protocol_version_reply";
    public static final String PROPERTY_DISABLED_FEATURES = "qpid.broker_disabled_features";

    public static final int  DEFAULT_FRAME_SIZE = Integer.getInteger(PROPERTY_FRAME_SIZE, 65535);

    private BrokerProperties()
    {
    }

    public static Locale getLocale()
    {
        Locale locale = Locale.US;
        String localeSetting = System.getProperty(BrokerProperties.PROPERTY_LOCALE);
        if (localeSetting != null)
        {
            String[] localeParts = localeSetting.split("_");
            String language = (localeParts.length > 0 ? localeParts[0] : "");
            String country = (localeParts.length > 1 ? localeParts[1] : "");
            String variant = "";
            if (localeParts.length > 2)
            {
                variant = localeSetting.substring(language.length() + 1 + country.length() + 1);
            }
            locale = new Locale(language, country, variant);
        }
        return locale;
    }
}
