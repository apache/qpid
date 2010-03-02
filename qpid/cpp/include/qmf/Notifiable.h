#ifndef _QmfNotifiable_
#define _QmfNotifiable_

/*
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
 */

#include "qmf/QmfImportExport.h"

namespace qmf {

    /**
     * The Notifiable class is an interface that may be used in the application.  It provides
     * a single callback (notify) that is invoked when the agent or console has events to be
     * handled by the application.
     *
     * This interface should only be used in an application that has more event drivers than
     * just a single QMF interface.  For example, an application that already uses select or
     * poll to control execution can use the notify callback to awaken the select/poll call.
     *
     * No QMF operations should be performed from the notify callback.  It should only be used
     * to awaken an application thread that will then perform QMF operations.
     *
     * \ingroup qmfapi
     */
    class Notifiable {
    public:
        QMF_EXTERN virtual ~Notifiable() {}
        virtual void notify() = 0;
    };
}

#endif
