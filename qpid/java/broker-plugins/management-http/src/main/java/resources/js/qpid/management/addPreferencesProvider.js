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
        "dojo/query",
        "dojo/_base/window",
        "dijit/registry",
        "dojo/parser",
        "dojo/_base/array",
        "dojo/_base/event",
        'dojo/_base/json',
        "dojo/store/Memory",
        "dijit/form/FilteringSelect",
        "dojo/_base/connect",
        "dojo/dom-style",
        "dojo/string",
        "dojox/html/entities",
        "qpid/management/PreferencesProviderFields",
        "dojox/validate/us",
        "dojox/validate/web",
        "dijit/Dialog",
        "dijit/form/CheckBox",
        "dijit/form/Textarea",
        "dijit/form/TextBox",
        "dijit/form/ValidationTextBox",
        "dijit/form/Button",
        "dijit/form/Form",
        "dojox/form/BusyButton",
        "dojox/form/CheckedMultiSelect",
        "dojox/layout/TableContainer",
        "dojo/domReady!"],
    function (xhr, dom, construct, query, win, registry, parser, array, event, json, Memory, FilteringSelect, connect, domStyle, string, entities, PreferencesProviderFields) {

        var addPreferencesProvider = {};

        var node = construct.create("div", null, win.body(), "last");

        xhr.get({url: "addPreferencesProvider.html",
                 sync: true,
                 load:  function(data) {
                            node.innerHTML = data;
                            addPreferencesProvider.dialogNode = dom.byId("addPreferencesProvider");
                            parser.instantiate([addPreferencesProvider.dialogNode]);

                            var cancelButton = registry.byId("addPreferencesProvider.cancelButton");
                            cancelButton.on("click", function(){
                              registry.byId("addPreferencesProvider").hide();
                            });
                            var theForm = registry.byId("formAddPreferencesProvider");
                            theForm.on("submit", function(e) {

                                event.stop(e);
                                if(theForm.validate()){
                                    if(PreferencesProviderFields.save(addPreferencesProvider.authenticationProviderName))
                                    {
                                        registry.byId("addPreferencesProvider").hide();
                                    }
                                    return false;
                                }else{
                                    alert('Form contains invalid data.  Please correct first');
                                    return false;
                                }
                            });
                        }});

        addPreferencesProvider.show = function(authenticationProviderName, providerName) {
            this.authenticationProviderName = authenticationProviderName;
            PreferencesProviderFields.show(dom.byId("addPreferencesProvider.preferencesProvider"), providerName, authenticationProviderName)
            var dialog = registry.byId("addPreferencesProvider");
            dialog.set("title", (providerName ? "Edit preferences provider '" + entities.encode(String(providerName)) + "' " : "Add preferences provider ") + " for '" + entities.encode(String(authenticationProviderName)) + "' ")
            dialog.show();
        }

        return addPreferencesProvider;
    });