package org.apache.qpid.server.plugin;

import java.util.Comparator;

public class ProtocolEngineCreatorComparator implements Comparator<ProtocolEngineCreator>
{
    @Override
    public int compare(ProtocolEngineCreator pec1, ProtocolEngineCreator pec2)
    {
        final AMQPProtocolVersionWrapper v1 = new AMQPProtocolVersionWrapper(pec1.getVersion());
        final AMQPProtocolVersionWrapper v2 = new AMQPProtocolVersionWrapper(pec2.getVersion());

        if (v1.getMajor() != v2.getMajor())
        {
            return v1.getMajor() - v2.getMajor();
        }
        else if (v1.getMinor() != v2.getMinor())
        {
            return v1.getMinor() - v2.getMinor();
        }
        else if (v1.getPatch() != v2.getPatch())
        {
            return v1.getPatch() - v2.getPatch();
        }
        else
        {
            return 0;
        }
    }


}
