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
package org.apache.qpid.qmf;

import java.util.UUID;

public abstract class QMFObject<C extends QMFClass, D extends QMFObject.Delegate>
{
    private long _deleteTime;

    public interface Delegate
    {
        UUID getId();
        long getCreateTime();
    }


    private D _delegate;

    protected QMFObject(D delegate)
    {
        _delegate = delegate;
    }

    public D getDelegate()
    {
        return _delegate;
    }

    abstract public C getQMFClass();

    public final UUID getId()
    {
        return _delegate.getId();
    }

    public final long getCreateTime()
    {
        return _delegate.getCreateTime();
    }

    public final void setDeleteTime()
    {
        _deleteTime = System.currentTimeMillis();
    }

    public final long getDeleteTime()
    {
        return _deleteTime;
    }



    abstract public QMFCommand asConfigInfoCmd(long sampleTime);
    abstract public QMFCommand asInstrumentInfoCmd(long sampleTime);
    abstract public QMFCommand asGetQueryResponseCmd(final QMFGetQueryCommand queryCommand, long sampleTime);

}
