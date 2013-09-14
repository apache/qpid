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
    function (xhr, dom, construct, query, win, registry, parser, array, event, json, Memory, FilteringSelect, connect, domStyle, string) {

        var addPreferencesProvider = {};

        var node = construct.create("div", null, win.body(), "last");

        var convertToPreferencesProvider = function convertToPreferencesProvider(formValues)
        {
            var newProvider = {};

            newProvider.name = dijit.byId("preferencesProvider.name").value;
            newProvider.type = dijit.byId("preferencesProvider.type").value;
            var id = dojo.byId("preferencesProvider.id").value;
            if (id)
            {
                newProvider.id = id;
            }
            for(var propName in formValues)
            {
                if(formValues.hasOwnProperty(propName))
                {
                    if(formValues[ propName ] !== "") {
                        newProvider[ propName ] = formValues[propName];
                    }

                }
            }
            return newProvider;
        }

        var selectPreferencesProviderType = function(type) {
            if(type && string.trim(type) != "")
            {
                require(["qpid/management/authenticationprovider/preferences/" + type.toLowerCase() + "/add"],
                function(addType)
                {
                  addType.show(dom.byId("preferencesProvider.fieldsContainer"), addPreferencesProvider.data)
                });
            }
        }

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
                                    var newProvider = convertToPreferencesProvider(theForm.getValues());
                                    var that = this;
                                    var nameWidget = registry.byId("preferencesProvider.name")
                                    xhr.put({url: "rest/preferencesprovider/" +encodeURIComponent(addPreferencesProvider.authenticationProviderName) + "/" + encodeURIComponent(nameWidget.value),
                                             sync: true, handleAs: "json",
                                             headers: { "Content-Type": "application/json"},
                                             putData: json.toJson(newProvider),
                                             load: function(x) {that.success = true; },
                                             error: function(error) {that.success = false; that.failureReason = error;}});
                                    if(this.success === true)
                                    {
                                        registry.byId("addPreferencesProvider").hide();
                                    }
                                    else
                                    {
                                        alert("Error:" + this.failureReason);
                                    }
                                    return false;
                                }else{
                                    alert('Form contains invalid data.  Please correct first');
                                    return false;
                                }
                            });
                            xhr.get({
                                sync: true,
                                url: "rest/helper?action=ListPreferencesProvidersTypes",
                                handleAs: "json"
                            }).then(
                                function(data) {
                                    var preferencesProvidersTypes =  data;
                                    var storeData = [];
                                    for (var i =0 ; i < preferencesProvidersTypes.length; i++)
                                    {
                                        storeData[i]= {id: preferencesProvidersTypes[i], name: preferencesProvidersTypes[i]};
                                    }
                                    var store = new Memory({ data: storeData });
                                    var preferencesProviderTypesDiv = dom.byId("addPreferencesProvider.selectPreferencesProviderDiv");
                                    var input = construct.create("input", {id: "preferencesProviderType", required: true}, preferencesProviderTypesDiv);
                                    addPreferencesProvider.preferencesProviderTypeChooser = new FilteringSelect({ id: "preferencesProvider.type",
                                                                              name: "type",
                                                                              store: store,
                                                                              searchAttr: "name",
                                                                              required: true,
                                                                              onChange: selectPreferencesProviderType }, input);
                                    addPreferencesProvider.preferencesProviderTypeChooser.startup();
                            });
                        }});

        addPreferencesProvider.show = function(authenticationProviderName, providerName) {
            this.authenticationProviderName = authenticationProviderName;
            this.data = null;
            var that = this;
            var theForm = registry.byId("formAddPreferencesProvider");
            theForm.reset();
            dojo.byId("preferencesProvider.id").value="";
            var nameWidget = registry.byId("preferencesProvider.name");
            nameWidget.set("disabled", false);
            registry.byId("preferencesProvider.type").set("disabled", false);
            if (this.preferencesProviderTypeChooser)
            {
                this.preferencesProviderTypeChooser.set("disabled", false);
                this.preferencesProviderTypeChooser.set("value", null);
            }
            var dialog = registry.byId("addPreferencesProvider");
            if (providerName)
            {
                xhr.get({
                    url: "rest/preferencesprovider/"  +encodeURIComponent(authenticationProviderName) + "/" + encodeURIComponent(providerName),
                    sync: false,
                    handleAs: "json"
                }).then(
                   function(data) {
                       var provider = data[0];
                       var providerType = provider.type;
                       that.data = provider;
                       nameWidget.set("value", provider.name);
                       nameWidget.set("disabled", true);
                       that.preferencesProviderTypeChooser.set("value", providerType);
                       that.preferencesProviderTypeChooser.set("disabled", true);
                       dojo.byId("preferencesProvider.id").value=provider.id;
                       dialog.show();
               });
            }
            else
            {
              dialog.show();
            }
        }

        return addPreferencesProvider;
    });