/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
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
 *
 */
package org.apache.qpidity.transport;

import java.util.List;

import org.apache.qpidity.transport.codec.Decoder;
import org.apache.qpidity.transport.codec.Encodable;
import org.apache.qpidity.transport.codec.Encoder;


/**
 * Struct
 *
 * @author Rafael H. Schloming
 */

public abstract class Struct implements Encodable
{

    public static Struct create(int type)
    {
        return StructFactory.create(type);
    }

    public abstract int getStructType();

    public abstract List<Field<?,?>> getFields();

    public abstract int getSizeWidth();

    public abstract int getPackWidth();

    public final int getEncodedType()
    {
        int type = getStructType();
        if (type < 0)
        {
            throw new UnsupportedOperationException();
        }
        return type;
    }

    public abstract boolean hasTicket();

    private final boolean isBit(Field<?,?> f)
    {
        return f.getType().equals(Boolean.class);
    }

    private final boolean packed()
    {
        if (this instanceof Method)
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    private final boolean encoded(Field<?,?> f)
    {
        return !packed() || !isBit(f) && f.has(this);
    }

    private final int getFlagWidth()
    {
        return (getFields().size() + 7)/8;
    }

    private final int getPaddWidth()
    {
        int pw = getPackWidth() - getFlagWidth();
        assert pw > 0;
        return pw;
    }

    private final int getFlagCount()
    {
        return 8*getPackWidth();
    }

    private final int getReservedFlagCount()
    {
        return getFlagCount() - getFields().size();
    }

    public final void read(Decoder dec)
    {
        List<Field<?,?>> fields = getFields();

        assert fields.size() <= getFlagCount();

        if (packed())
        {
            for (Field<?,?> f : fields)
            {
                if (isBit(f))
                {
                    f.has(this, true);
                    f.read(dec, this);
                }
                else
                {
                    f.has(this, dec.readBit());
                }
            }

            for (int i = 0; i < getReservedFlagCount(); i++)
            {
                if (dec.readBit())
                {
                    throw new IllegalStateException("reserved flag true");
                }
            }
        }

        if (hasTicket())
        {
            dec.readShort();
        }

        for (Field<?,?> f : fields)
        {
            if (encoded(f))
            {
                f.read(dec, this);
            }
        }
    }

    public final void write(Encoder enc)
    {
        List<Field<?,?>> fields = getFields();

        assert fields.size() <= getFlagCount();

        if (packed())
        {
            for (Field<?,?> f : fields)
            {
                if (isBit(f))
                {
                    f.write(enc, this);
                }
                else
                {
                    enc.writeBit(f.has(this));
                }
            }

            for (int i = 0; i < getReservedFlagCount(); i++)
            {
                enc.writeBit(false);
            }
        }

        if (hasTicket())
        {
            enc.writeShort(0x0);
        }

        for (Field<?,?> f : fields)
        {
            if (encoded(f))
            {
                f.write(enc, this);
            }
        }
    }

    public String toString()
    {
        StringBuilder str = new StringBuilder();
        str.append(getClass().getSimpleName());

        str.append("(");
        boolean first = true;
        for (Field<?,?> f : getFields())
        {
            if (packed())
            {
                if (!f.has(this))
                {
                    continue;
                }
            }

            if (first)
            {
                first = false;
            }
            else
            {
                str.append(", ");
            }
            str.append(f.getName());
            str.append("=");
            str.append(f.get(this));
        }
        str.append(")");

        return str.toString();
    }

}
