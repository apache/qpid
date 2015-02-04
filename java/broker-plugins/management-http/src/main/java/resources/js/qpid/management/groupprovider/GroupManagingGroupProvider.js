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
        "dojo/parser",
        "dojo/query",
        "dojo/dom-construct",
        "dojo/_base/connect",
        "dojo/_base/window",
        "dojo/_base/event",
        "dojo/_base/json",
        "dijit/registry",
        "dojox/html/entities",
        "qpid/common/util",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/UpdatableStore",
        "dojox/grid/EnhancedGrid",
        "dojo/text!groupprovider/showGroupManagingGroupProvider.html",
        "dojox/grid/enhanced/plugins/Pagination",
        "dojox/grid/enhanced/plugins/IndirectSelection",
        "dojox/validate/us", "dojox/validate/web",
        "dijit/Dialog",
        "dijit/form/TextBox",
        "dijit/form/ValidationTextBox",
        "dijit/form/TimeTextBox", "dijit/form/Button",
        "dijit/form/Form",
        "dijit/form/DateTextBox",
        "dojo/domReady!"],
    function (xhr, dom, parser, query, construct, connect, win, event, json, registry, entities, util, properties,
              updater, UpdatableStore, EnhancedGrid, template)
    {
        function GroupManagingGroupProvider(containerNode, groupProviderObj, controller)
        {
            var node = construct.create("div", null, containerNode, "last");
            var that = this;
            this.name = groupProviderObj.name;
            node.innerHTML = template;
            parser.parse(node);
            this.controller = controller;

                     var groupDiv = query(".groups", node)[0];

                     var gridProperties = {
                                            height: 400,
                                            keepSelection: true,
                                            plugins: {
                                                      pagination: {
                                                          pageSizes: [10, 25, 50, 100],
                                                          description: true,
                                                          sizeSwitch: true,
                                                          pageStepper: true,
                                                          gotoButton: true,
                                                          maxPageStep: 4,
                                                          position: "bottom"
                                                      },
                                                      indirectSelection: true

                                             }};
            this.groupsGrid = new UpdatableStore([], groupDiv,
                                        [ { name: "Group Name",    field: "name",      width: "100%" }
                                        ], function(obj) {
                                          connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                              function(evt){
                                                  var idx = evt.rowIndex,
                                                      theItem = this.getItem(idx);
                                                  var name = obj.dataStore.getValue(theItem,"name");
                                                  that.controller.show("group", name, groupProviderObj, theItem.id);
                                              });
                                      }, gridProperties, EnhancedGrid);
            var addGroupButton = query(".addGroupButton", node)[0];
            registry.byNode(addGroupButton).on("click", function(evt){ addGroup.show(groupProviderObj.name) });
            var deleteWidget = registry.byNode(query(".deleteGroupButton", node)[0]);
            deleteWidget.on("click", function(evt){ event.stop(evt); that.deleteGroups(); });
        }

        GroupManagingGroupProvider.prototype.deleteGroups = function()
        {
            var grid = this.groupsGrid.grid;
            var data = grid.selection.getSelected();
            if(data.length) {
                var that = this;
                if(confirm("Delete " + data.length + " groups?")) {
                    var i, queryParam;
                    for(i = 0; i<data.length; i++) {
                        if(queryParam) {
                            queryParam += "&";
                        } else {
                            queryParam = "?";
                        }

                        queryParam += "id=" + data[i].id;
                    }
                    var query = "api/latest/group/"+ encodeURIComponent(that.name)
                       + queryParam;
                    that.success = true
                    xhr.del({url: query, sync: true, handleAs: "json"}).then(
                        function(data) {
                            grid.setQuery({id: "*"});
                            grid.selection.deselectAll();
                            that.update();
                        },
                        function(error) {that.success = false; that.failureReason = error;});
                    if(!that.success ) {
                        util.xhrErrorHandler(this.failureReason);
                    }
                }
}
        };

        GroupManagingGroupProvider.prototype.update = function(data)
        {
            if (data)
            {
                this.groupsGrid.update(data.groups);
            }
        };

        var addGroup = {};

        var node = construct.create("div", null, win.body(), "last");

        var convertToGroup = function convertToGroup(formValues) {
                var newGroup = {};
                newGroup.name = formValues.name;
                for(var propName in formValues)
                {
                    if(formValues.hasOwnProperty(propName)) {
                            if(formValues[ propName ] !== "") {
                                newGroup[ propName ] = formValues[propName];
                            }
                    }
                }

                return newGroup;
            };


        xhr.get({url: "groupprovider/addGroup.html",
                 sync: true,
                 load:  function(data) {
                            var theForm;
                            node.innerHTML = data;
                            addGroup.dialogNode = dom.byId("addGroup");
                            parser.instantiate([addGroup.dialogNode]);

                            var that = this;

                            theForm = registry.byId("formAddGroup");
                            theForm.on("submit", function(e) {

                                event.stop(e);
                                if(theForm.validate()){

                                    var newGroup = convertToGroup(theForm.getValues());


                                    var url = "api/latest/group/"+encodeURIComponent(addGroup.groupProvider) +
                                        "/"+encodeURIComponent(newGroup.name);

                                    xhr.put({url: url, sync: true, handleAs: "json",
                                             headers: { "Content-Type": "application/json"},
                                             putData: json.toJson(newGroup),
                                             load: function(x) {that.success = true; },
                                             error: function(error) {that.success = false; that.failureReason = error;}});

                                    if(that.success === true) {
                                        registry.byId("addGroup").hide();
                                    } else {
                                        util.xhrErrorHandler(this.failureReason);
                                    }

                                    return false;


                                }else{
                                    alert('Form contains invalid data.  Please correct first');
                                    return false;
                                }

                            });
                        }});

        addGroup.show = function(groupProvider) {
            addGroup.groupProvider = groupProvider;
            registry.byId("formAddGroup").reset();
            registry.byId("addGroup").show();
        };

        return GroupManagingGroupProvider;
    });
