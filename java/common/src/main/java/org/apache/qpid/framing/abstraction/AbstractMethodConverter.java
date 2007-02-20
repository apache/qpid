package org.apache.qpid.framing.abstraction;

public abstract class AbstractMethodConverter implements ProtocolVersionMethodConverter
{
    private final byte _protocolMajorVersion;


    private final byte _protocolMinorVersion;

    public AbstractMethodConverter(byte major, byte minor)
    {
        _protocolMajorVersion = major;
        _protocolMinorVersion = minor;
    }


    public final byte getProtocolMajorVersion()
    {
        return _protocolMajorVersion;
    }

    public final byte getProtocolMinorVersion()
    {
        return _protocolMinorVersion;
    }
}
