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
define(["dojo/_base/xhr",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/_base/window",
        "dijit/registry",
        "dojo/parser",
        "dojo/_base/array",
        "dojo/json",
        "dojo/query",
        "dojo/_base/connect",
        "dojo/_base/event",
        "dojo/store/Memory",
        "dojox/grid/DataGrid",
        "dojo/data/ObjectStore",
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, parser, array, json, query, connect, event, Memory, DataGrid, ObjectStore) {
        var nodeFields = ["storePath", "groupName", "nodeName", "state", "role", "hostPort", "helperHostPort",
                    "coalescingSync", "designatedPrimary", "durability", "priority",
                    "quorumOverride"];

        return {
            show: function() {

                var node = dom.byId("addVirtualHost.typeSpecificDiv");
                var that = this;

                array.forEach(registry.toArray(),
                              function(item) {
                                  if(item.id.substr(0,27) == "formAddVirtualHost.specific") {
                                      item.destroyRecursive();
                                  }
                              });

                xhr.get({url: "virtualhost/bdb_ha/add.html",
                     sync: true,
                     load:  function(data) {
                                node.innerHTML = data;
                                parser.parse(node);
                                for(var i = 0; i < nodeFields.length; i++)
                                {
                                  that[nodeFields[i]] = registry.byId("formAddVirtualHost.specific." + nodeFields[i]);
                                }

                                that._initSettingsUI();
                     }});
            },
            save: function()
            {
              this.success = false;
              var that = this;
              var virtualHostName = registry.byId("formAddVirtualHost.name").get("value");
              var virtualHostNameEncoded = encodeURIComponent(virtualHostName);


              // create virtual host in QUIESCED state
              var hostData =
              {
                  state: "QUIESCED",
                  name: virtualHostName,
                  type: registry.byId("addVirtualHost.type").get("value"),
              };

              xhr.put({url: "rest/virtualhost/" + virtualHostNameEncoded,
                sync: true, handleAs: "json",
                headers: { "Content-Type": "application/json"},
                putData: json.stringify(hostData),
                load: function(x) { that.success = true; },
                error: function(error) {that.success = false; that.failureReason = error;}});

              // if success, create node
              if (this.success)
              {
                var node = {};
                for(var i = 0; i < nodeFields.length; i++)
                {
                  var fieldName = nodeFields[i];
                  var widget = this[fieldName];
                  if (widget)
                  {
                    node[fieldName] = widget.type=="checkbox"? widget.get("checked"): widget.get("value");
                  }
                }

                node.name = this.nodeName.value;

                var data = this.settingsStore.objectStore.data;
                if (data.length > 0 )
                {
                  var parameters = null;
                  var replicationParameters = null;

                  for(var i=0; i<data.length; i++)
                  {
                    if (data[i].name && data[i].value)
                    {
                      if (data[i].name.indexOf("je.rep.") == 0)
                      {
                        if (replicationParameters == null)
                        {
                          replicationParameters = {};
                        }
                        replicationParameters[data[i].name] = data[i].value;
                      }
                      else
                      {
                        if (parameters == null)
                        {
                          parameters = {};
                        }
                        parameters[data[i].name] = data[i].value;
                      }
                    }
                  }

                  if (parameters)
                  {
                    node["parameters"] = parameters;
                  }

                  if (replicationParameters)
                  {
                    node["replicationParameters"] = replicationParameters;
                  }
                }

                xhr.put({url: "rest/replicationnode/" + virtualHostNameEncoded + "/" + encodeURIComponent(this.nodeName.value),
                  sync: true, handleAs: "json",
                  headers: { "Content-Type": "application/json"},
                  putData: json.stringify(node),
                  load: function(x) { that.success = true; },
                  error: function(error) {that.success = false; that.failureReason = error;}});

                // if success, change virtual host state to ACTIVE
                if (this.success)
                {
                  xhr.put({url: "rest/virtualhost/" + virtualHostNameEncoded,
                    sync: true, handleAs: "json",
                    headers: { "Content-Type": "application/json"},
                    putData: json.stringify({state: "ACTIVE"}),
                    load: function(x) { that.success = true; },
                    error: function(error) {that.success = false; that.failureReason = error;}});
                }
              }

              return this.success;
            },

            _initSettingsUI: function()
            {
              var that = this;
              var layout = [[
                             {'name': 'Name', 'field': 'name', 'width': '200px', 'editable': true},
                             {'name': 'Value', 'field': 'value', 'width': 'auto', 'editable': true}
                           ]];
              this.idGenerator = 0;
              this.settingsStore= new ObjectStore({objectStore: new Memory({data: [], idProperty: "id"})});
              this.settings = new DataGrid({
                id: 'formAddVirtualHost.specific.jeSettingsGrid',
                store: this.settingsStore,
                structure: layout,
                rowSelector: '20px',
                query:{ name: '*' },
                rowsPerPage:20,
                selectionMode: 'multiple',
                disable: true},
                "formAddVirtualHost.specific.jeSettings");

              this.removeSettingButton = registry.byId("formAddVirtualHost.specific.removeSetting");
              this.removeSettingButton.set("disabled", true);

              connect.connect(this.settings.selection, 'onSelected',  function(rowIndex){
                var data = that.settings.selection.getSelected();
                that.removeSettingButton.set("disabled",!data.length );
              });

              this.settings.startup();

              registry.byId("formAddVirtualHost.specific.addSetting").on("click", function(){
                var rowIndex = that.settingsStore.objectStore.data.length;
                var myNewItem = {id: (that.idGenerator++), name: "", value: ""};
                that.settingsStore.objectStore.data.push(myNewItem);
                that.settingsStore.objectStore.setData(that.settingsStore.objectStore.data);
                that.settings.set("disabled", false);
                that.removeSettingButton.set("disabled", false);
                that.settings._refresh();
                that.settings.focus.setFocusIndex( rowIndex, 0 );
                that.settings.edit.setEditCell( that.settings.focus.cell, rowIndex );
              });

              this.removeSettingButton.on("click", function(){
                var items = that.settings.selection.getSelected();
                if(items.length){
                    var data = that.settingsStore.objectStore.data;

                    array.forEach(items, function(selectedItem){
                        if(selectedItem !== null){
                          for(var i=0; i<data.length;)
                          {
                            if (data[i].id==selectedItem.id)
                            {
                              data.splice(i, 1);
                              break;
                            }
                            else
                            {
                              i++;
                            }
                          }
                        }
                    });
                    that.settingsStore.objectStore.setData(data);
                }

                if (that.settingsStore.objectStore.data.length == 0)
                {
                  that.settings.set("disabled", true);
                  that.removeSettingButton.set("disabled", true);
                }

                that.settings._refresh();
                event.stop(e);
              });
            }
        };
    });
