package org.apache.qpid.server.plugin;

import org.apache.commons.lang.StringUtils;
import org.apache.qpid.server.model.Protocol;

public class AMQPProtocolVersionWrapper
{
    private static final char DELIMITER = '_';

    private int major;
    private int minor;
    private int patch;

    public AMQPProtocolVersionWrapper(Protocol amqpProtocol)
    {
        if (!amqpProtocol.isAMQP())
        {
            throw new IllegalArgumentException("Protocol must be of type " + Protocol.ProtocolType.AMQP);
        }

        final String[] parts = StringUtils.split(amqpProtocol.name(), DELIMITER);
        for (int i = 0; i < parts.length; i++)
        {
            switch (i)
            {
                case 1: this.major = Integer.parseInt(parts[i]);
                    break;
                case 2: this.minor = Integer.parseInt(parts[i]);
                    break;
                case 3: this.patch = Integer.parseInt(parts[i]);
                    break;
            }
        }
    }

    public AMQPProtocolVersionWrapper(int major, int minor, int patch)
    {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public int getMajor()
    {
        return major;
    }

    public void setMajor(int major)
    {
        this.major = major;
    }

    public int getMinor()
    {
        return minor;
    }

    public void setMinor(int minor)
    {
        this.minor = minor;
    }

    public int getPatch()
    {
        return patch;
    }

    public void setPatch(int patch)
    {
        this.patch = patch;
    }

    public Protocol getProtocol()
    {
        return Protocol.valueOf(this.toString());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof AMQPProtocolVersionWrapper))
        {
            return false;
        }

        final AMQPProtocolVersionWrapper number = (AMQPProtocolVersionWrapper) o;

        if (this.major != number.major)
        {
            return false;
        }
        else if (this.minor != number.minor)
        {
            return false;
        }
        else if (this.patch != number.patch)
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    @Override
    public int hashCode()
    {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + patch;
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder(Protocol.ProtocolType.AMQP.name()).append(DELIMITER)
                                     .append(major).append(DELIMITER)
                                     .append(minor);
        if (patch != 0)
        {
            sb.append(DELIMITER).append(patch);
        }
        return sb.toString();
    }
}
