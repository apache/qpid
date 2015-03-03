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
define(["qpid/common/util",
        "dojo/text!service/helper?action=pluginList&plugin=JDBCConnectionProviderFactory",
        "dojo/_base/array",
        "dojo/json",
        "dojo/string",
        "dojo/store/Memory",
        "dojo/dom",
        "dojo/dom-construct",
        "dijit/registry",
        "dojo/domReady!"],
   function (util, poolTypeJsonString, array, json, string, Memory, dom, domConstruct, registry)
   {

       return {
           show: function(data)
           {
              var that = this;
              util.parseHtmlIntoDiv(data.containerNode, "virtualhost/jdbc/edit.html",
                function(){that._postParse(data)});
           },
           _postParse: function(data)
           {
              registry.byId("editVirtualHost.connectionUrl").set("regExpGen", util.jdbcUrlOrContextVarRegexp);
              registry.byId("editVirtualHost.username").set("regExpGen", util.nameOrContextVarRegexp);

              var poolTypes = json.parse(poolTypeJsonString);
              var poolTypesData = [];
              for (var i =0 ; i < poolTypes.length; i++)
              {
                  poolTypesData[i]= {id: poolTypes[i], name: poolTypes[i]};
              }
              var poolTypesStore = new Memory({ data: poolTypesData });
              var poolTypeControl = registry.byId("editVirtualHost.connectionPoolType");
              poolTypeControl.set("store", poolTypesStore);
              poolTypeControl.set("value", data.data.connectionPoolType);

              var passwordControl = registry.byId("editVirtualHost.password");
              if (data.data.password)
              {
                passwordControl.set("placeHolder", "*******");
              }

               var poolTypeFieldsDiv = dom.byId("editVirtualHost.poolSpecificDiv");
               poolTypeControl.on("change",
                      function(type)
                      {
                        if(type && string.trim(type) != "")
                        {
                            var widgets = registry.findWidgets(poolTypeFieldsDiv);
                            array.forEach(widgets, function(item) { item.destroyRecursive();});
                            domConstruct.empty(poolTypeFieldsDiv);

                            require(["qpid/management/store/pool/"+type.toLowerCase()+"/edit"],
                            function(poolType)
                            {
                                poolType.show({containerNode:poolTypeFieldsDiv, data: data.data, context: data.parent.context})
                            });
                        }
                      }
               );
           }
       };
   }
);
