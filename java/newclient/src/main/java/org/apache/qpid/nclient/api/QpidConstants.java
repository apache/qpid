package org.apache.qpid.nclient.api;


public class QpidConstants
{
	public static final int SESSION_EXPIRY_TIED_TO_CHANNEL = 0;
	public static final int SESSION_EXPIRY_MAX_TIME = Integer.MAX_VALUE;

	public final static String TOPIC_EXCHANGE_NAME = "amq.topic";

    public final static String TOPIC_EXCHANGE_CLASS = "topic";

    public final static String DIRECT_EXCHANGE_NAME = "amq.direct";

    public final static String DIRECT_EXCHANGE_CLASS = "direct";

    public final static String HEADERS_EXCHANGE_NAME = "amq.match";

    public final static String HEADERS_EXCHANGE_CLASS = "headers";

    public final static String FANOUT_EXCHANGE_NAME = "amq.fanout";

    public final static String FANOUT_EXCHANGE_CLASS = "fanout";

    public final static String SYNAPSE_EXCHANGE_NAME = "amq.synapse";

    public final static String SYNAPSE_EXCHANGE_CLASS = "synapse";
    

    public final static String SYSTEM_MANAGEMENT_EXCHANGE_NAME = "qpid.sysmgmt";

    public final static String SYSTEM_MANAGEMENT_CLASS = "sysmmgmt";
}
