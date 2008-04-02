package org.apache.qpidity.transport;

public class TransportConstants
{
    private static byte _protocol_version_minor = 0;
    private static byte _protocol_version_major = 99;

    public static void setVersionMajor(byte value)
    {
        _protocol_version_major = value;
    }

    public static void setVersionMinor(byte value)
    {
        _protocol_version_minor = value;
    }

    public static byte getVersionMajor()
    {
        return _protocol_version_major;
    }

    public static byte getVersionMinor()
    {
        return _protocol_version_minor;
    }
}
