package org.apache.qpid.server.store.berkeleydb.tuple;

import org.apache.qpid.server.store.ConfiguredObjectRecord;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class ConfiguredObjectBinding extends TupleBinding<ConfiguredObjectRecord>
{
    private static final ConfiguredObjectBinding INSTANCE = new ConfiguredObjectBinding();

    public static ConfiguredObjectBinding getInstance()
    {
        return INSTANCE;
    }

    /** non-public constructor forces getInstance instead */
    private ConfiguredObjectBinding()
    {
    }

    public ConfiguredObjectRecord entryToObject(TupleInput tupleInput)
    {
        String type = tupleInput.readString();
        String json = tupleInput.readString();
        ConfiguredObjectRecord configuredObject = new ConfiguredObjectRecord(null, type, json);
        return configuredObject;
    }

    public void objectToEntry(ConfiguredObjectRecord object, TupleOutput tupleOutput)
    {
        tupleOutput.writeString(object.getType());
        tupleOutput.writeString(object.getAttributes());
    }

}
