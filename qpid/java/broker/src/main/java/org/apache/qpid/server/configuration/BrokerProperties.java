package org.apache.qpid.server.configuration;

import java.util.Locale;

/**
 * Declares broker system property names
 */
public class BrokerProperties
{
    public static final int  DEFAULT_HEART_BEAT_TIMEOUT_FACTOR = 2;

    public static final String PROPERTY_DEAD_LETTER_EXCHANGE_SUFFIX = "qpid.dead_letter_exchange_suffix";
    public static final String PROPERTY_DEAD_LETTER_QUEUE_SUFFIX = "qpid.dead_letter_queue_suffix";

    public static final String PROPERTY_FRAME_SIZE = "qpid.frame_size";
    public static final String PROPERTY_MSG_AUTH = "qpid.msg_auth";
    public static final String PROPERTY_STATUS_UPDATES = "qpid.status_updates";
    public static final String PROPERTY_LOCALE = "qpid.locale";
    public static final String PROPERTY_DEFAULT_SUPPORTED_PROTOCOL_REPLY = "qpid.default_supported_protocol_version_reply";
    public static final String PROPERTY_DISABLED_FEATURES = "qpid.broker_disabled_features";

    public static final int  DEFAULT_FRAME_SIZE = Integer.getInteger(PROPERTY_FRAME_SIZE, 65535);
    public static final String PROPERTY_BROKER_DEFAULT_AMQP_PROTOCOL_EXCLUDES = "qpid.broker_default_amqp_protocol_excludes";
    public static final String PROPERTY_BROKER_DEFAULT_AMQP_PROTOCOL_INCLUDES = "qpid.broker_default_amqp_protocol_includes";

    public static final String PROPERTY_MANAGEMENT_RIGHTS_INFER_ALL_ACCESS = "qpid.broker_jmx_method_rights_infer_all_access";
    public static final String PROPERTY_USE_CUSTOM_RMI_SOCKET_FACTORY = "qpid.broker_jmx_use_custom_rmi_socket_factory";

    public static final String PROPERTY_QPID_HOME = "QPID_HOME";
    public static final String PROPERTY_QPID_WORK = "QPID_WORK";

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
