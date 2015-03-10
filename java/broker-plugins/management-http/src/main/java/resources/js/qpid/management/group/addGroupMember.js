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
        "dojo/_base/event",
        "dojo/_base/json",
        "qpid/common/util",
        "dijit/form/NumberSpinner", // required by the form
        /* dojox/ validate resources */
        "dojox/validate/us", "dojox/validate/web",
        /* basic dijit classes */
        "dijit/Dialog",
        "dijit/form/CheckBox", "dijit/form/Textarea",
        "dijit/form/FilteringSelect", "dijit/form/TextBox",
        "dijit/form/ValidationTextBox", "dijit/form/DateTextBox",
        "dijit/form/TimeTextBox", "dijit/form/Button",
        "dijit/form/RadioButton", "dijit/form/Form",
        "dijit/form/DateTextBox",
        /* basic dojox classes */
        "dojox/form/BusyButton", "dojox/form/CheckedMultiSelect",
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, parser, array, event, json, util) {

        var addGroupMember = {};

        var node = construct.create("div", null, win.body(), "last");

        var convertToGroupMember = function convertToGroupMember(formValues)
            {
                var newGroupMember = {};
                newGroupMember.name = formValues.name;
                return newGroupMember;
            };

        xhr.get({url: "group/addGroupMember.html",
                 sync: true,
                 load:  function(data) {
                            var theForm;
                            node.innerHTML = data;
                            addGroupMember.dialogNode = dom.byId("addGroupMember");
                            parser.instantiate([addGroupMember.dialogNode]);

                            theForm = registry.byId("formAddGroupMember");
                            theForm.on("submit", function(e) {

                                event.stop(e);
                                if(theForm.validate()){

                                    var newGroupMember = convertToGroupMember(theForm.getValues());
                                    var that = this;

                                    var url = "api/latest/groupmember/"+encodeURIComponent(addGroupMember.groupProvider) +
                                              "/" + encodeURIComponent(addGroupMember.group) +
                                              "/" + encodeURIComponent(newGroupMember.name);
                                    util.post(url, newGroupMember, function(x){registry.byId("addGroupMember").hide();});
                                    return false;


                                }else{
                                    alert('Form contains invalid data.  Please correct first');
                                    return false;
                                }

                            });
                        }});

        addGroupMember.show = function(groupProvider, group) {
                            addGroupMember.groupProvider = groupProvider;
                            addGroupMember.group = group;
                            registry.byId("formAddGroupMember").reset();
                            registry.byId("addGroupMember").show();
                        };

        return addGroupMember;
    });