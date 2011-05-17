#ifndef QMF_DATA_ADDR_H
#define QMF_DATA_ADDR_H
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

#include <qmf/ImportExport.h>
#include "qmf/Handle.h"
#include "qpid/types/Variant.h"
#include <string>

namespace qmf {

#ifndef SWIG
    template <class> class PrivateImplRef;
#endif

    class DataAddrImpl;

    class QMF_CLASS_EXTERN DataAddr : public qmf::Handle<DataAddrImpl> {
    public:
        QMF_EXTERN DataAddr(DataAddrImpl* impl = 0);
        QMF_EXTERN DataAddr(const DataAddr&);
        QMF_EXTERN DataAddr& operator=(const DataAddr&);
        QMF_EXTERN ~DataAddr();

        QMF_EXTERN bool operator==(const DataAddr&);
        QMF_EXTERN bool operator<(const DataAddr&);

        QMF_EXTERN DataAddr(const qpid::types::Variant::Map&);
        QMF_EXTERN DataAddr(const std::string& name, const std::string& agentName, uint32_t agentEpoch=0);
        QMF_EXTERN const std::string& getName() const;
        QMF_EXTERN const std::string& getAgentName() const;
        QMF_EXTERN uint32_t getAgentEpoch() const;
        QMF_EXTERN qpid::types::Variant::Map asMap() const;

#ifndef SWIG
    private:
        friend class qmf::PrivateImplRef<DataAddr>;
        friend struct DataAddrImplAccess;
#endif
    };

}

#endif
