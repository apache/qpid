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

define(["dojo/query",
        "qpid/common/util",
        "qpid/common/metadata",
        "dojox/grid/DataGrid",
        "qpid/common/UpdatableStore",
        "qpid/management/UserPreferences",
        "dojo/domReady!"],
  function (query, util, metadata, DataGrid, UpdatableStore, UserPreferences)
  {


    function NonJavaTrustStore(data)
    {
        this.fields = [];
        var attributes = metadata.getMetaData("TrustStore", "NonJavaTrustStore").attributes;
        for(var name in attributes)
        {
            this.fields.push(name);
        }
        util.buildUI(data.containerNode, data.parent, "store/nonjavatruststore/show.html", this.fields, this);
        var gridNode = query(".details", data.containerNode)[0];
        var dateTimeFormatter = function(value){ return value ? UserPreferences.formatDateTime(value, {addOffset: true, appendTimeZone: true}) : "";};
        this.detailsGrid = new UpdatableStore([],
                  gridNode,
                  [
                   { name: 'Subject', field: 'SUBJECT_NAME', width: '25%' },
                   { name: 'Issuer', field: 'ISSUER_NAME', width: '25%' },
                   { name: 'Valid from', field: 'VALID_START', width: '25%', formatter: dateTimeFormatter },
                   { name: 'Valid to', field: 'VALID_END', width: '25%', formatter: dateTimeFormatter}
                  ]);
    }

    NonJavaTrustStore.prototype.update = function(data)
    {
        util.updateUI(data, this.fields, this);
        var details = data.certificateDetails;
        for(var i=0; i < details.length; i++)
        {
            details[i].id = details[i].SUBJECT_NAME + "_" +  details[i].ISSUER_NAME + "_" + details[i].VALID_START + "_" + details[i].VALID_END;
        }
        this.detailsGrid.grid.beginUpdate();
        this.detailsGrid.update(details);
        this.detailsGrid.grid.endUpdate();
    }

    return NonJavaTrustStore;
  }
);
