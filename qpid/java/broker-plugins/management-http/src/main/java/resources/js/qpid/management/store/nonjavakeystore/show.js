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

define(["qpid/common/util", "qpid/common/metadata", "qpid/management/UserPreferences", "dojox/html/entities", "dojo/domReady!"],
  function (util, metadata, UserPreferences, entities)
  {

    function toDate(value)
    {
        return value ? entities.encode(String(UserPreferences.formatDateTime(value))) : "";
    }

    var dateFields = ["certificateValidEnd","certificateValidStart"];

    function NonJavaKeyStore(data)
    {
        this.fields = [];
        var attributes = metadata.getMetaData("KeyStore", "NonJavaKeyStore").attributes;
        for(var name in attributes)
        {
            if (dateFields.indexOf(name) == -1)
            {
                this.fields.push(name);
            }
        }
        var allFields = this.fields.concat(dateFields);
        util.buildUI(data.containerNode, data.parent, "store/nonjavakeystore/show.html",allFields, this);
    }

    NonJavaKeyStore.prototype.update = function(data)
    {
        util.updateUI(data, this.fields, this);
        if (data)
        {
            for(var idx in dateFields)
            {
                var name = dateFields[idx];
                this[name].innerHTML = toDate(data[name]);
            }
        }
    }

    return NonJavaKeyStore;
  }
);
