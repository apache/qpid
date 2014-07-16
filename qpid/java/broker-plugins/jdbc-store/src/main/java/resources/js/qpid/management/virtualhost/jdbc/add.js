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
define(["dojo/_base/xhr",
        "dojo/_base/array",
        "dojo/parser",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/json",
        "dojo/string",
        "dojo/store/Memory",
        "dijit/registry",
        "dojo/text!virtualhost/jdbc/add.html",
        "dojo/text!service/helper?action=pluginList&plugin=JDBCConnectionProviderFactory",
        "dijit/form/ValidationTextBox",
        "dijit/form/CheckBox",
        "dojo/domReady!"],
  function (xhr, array, parser, dom, domConstruct, json, string, Memory, registry, template, poolTypeJsonString)
  {
    return {
        show: function (data)
        {
            this.containerNode = domConstruct.create("div", {innerHTML: template}, data.containerNode);
            parser.parse(this.containerNode);

            var poolTypes = json.parse(poolTypeJsonString);
            var poolTypesData = [];
            for (var i =0 ; i < poolTypes.length; i++)
            {
                poolTypesData[i]= {id: poolTypes[i], name: poolTypes[i]};
            }
            var poolTypesStore = new Memory({ data: poolTypesData });
            var poolTypeControl = registry.byId("addVirtualHost.connectionPoolType");
            poolTypeControl.set("store", poolTypesStore);

            var poolTypeFieldsDiv = dom.byId("addVirtualHost.poolSpecificDiv");
            poolTypeControl.on("change",
                    function(type)
                    {
                      if(type && string.trim(type) != "")
                      {
                          var widgets = registry.findWidgets(poolTypeFieldsDiv);
                          array.forEach(widgets, function(item) { item.destroyRecursive();});
                          domConstruct.empty(poolTypeFieldsDiv);
                          require(["qpid/management/store/pool/"+type.toLowerCase()+"/add"],
                          function(poolType)
                          {
                              poolType.show("addVirtualHost.poolSpecificDiv", "formAddVirtualHost.qpid.jdbcstore.bonecp", "virtualhost/store/pool/"+type.toLowerCase()+"/add.html");
                          });
                      }
                    }
            );
         }
    };
  }
);
