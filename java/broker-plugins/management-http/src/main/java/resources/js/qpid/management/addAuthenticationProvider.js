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
        'dojo/json',
        "dojo/store/Memory",
        "dijit/form/FilteringSelect",
        "dojo/_base/connect",
        "dojo/dom-style",
        "qpid/common/util",
        "qpid/common/metadata",
        "dojo/text!addAuthenticationProvider.html",
        "qpid/management/preferencesprovider/PreferencesProviderForm",
        /* dojox/ validate resources */
        "dojox/validate/us", "dojox/validate/web",
        /* basic dijit classes */
        "dijit/Dialog",
        "dijit/form/CheckBox", "dijit/form/Textarea",
        "dijit/form/TextBox",
        "dijit/form/ValidationTextBox",
        "dijit/form/Button",
        "dijit/form/Form",
        /* basic dojox classes */
        "dojox/form/BusyButton", "dojox/form/CheckedMultiSelect",
        "dojox/layout/TableContainer",
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, parser, array, event, json, Memory, FilteringSelect, connect, domStyle, util, metadata, template)
    {
        var addAuthenticationProvider =
        {
            init:function()
            {
                var that=this;
                this.containerNode = construct.create("div", {innerHTML: template});
                parser.parse(this.containerNode);

                this.authenticationProviderName = registry.byId("addAuthenticationProvider.name");
                this.authenticationProviderName.set("regExpGen", util.nameOrContextVarRegexp);

                this.dialog = registry.byId("addAuthenticationProvider");
                this.addButton = registry.byId("addAuthenticationProvider.addButton");
                this.cancelButton = registry.byId("addAuthenticationProvider.cancelButton");
                this.cancelButton.on("click", function(e){that._cancel(e);});
                this.addButton.on("click", function(e){that._add(e);});

                this.authenticationProviderTypeFieldsContainer = dom.byId("addAuthenticationProvider.typeFields");
                this.authenticationProviderForm = registry.byId("addAuthenticationProvider.form");
                this.authenticationProviderType = registry.byId("addAuthenticationProvider.type");
                this.supportedAuthenticationProviderTypes = metadata.getTypesForCategory("AuthenticationProvider");
                this.supportedAuthenticationProviderTypes.sort();
                var authenticationProviderTypeStore = util.makeTypeStore(this.supportedAuthenticationProviderTypes);
                this.authenticationProviderType.set("store", authenticationProviderTypeStore);
                this.authenticationProviderType.on("change", function(type){that._authenticationProviderTypeChanged(type);});

                this.preferencesProviderForm = new qpid.preferencesprovider.PreferencesProviderForm({disabled: true});
                this.preferencesProviderForm.placeAt(dom.byId("addPreferencesProvider.form"));
            },
            show:function(effectiveData)
            {
                this.authenticationProviderForm.reset();
                this.preferencesProviderForm.reset();

                if (effectiveData)
                {
                    // editing
                    var actualData = null;
                    xhr.get(
                                {
                                  url: "api/latest/authenticationprovider/" + encodeURIComponent(effectiveData.name),
                                  sync: true,
                                  content: { actuals: true },
                                  handleAs: "json",
                                  load: function(data)
                                  {
                                    actualData = data[0];
                                  }
                                }
                            );
                    this.initialData = actualData;
                    this.effectiveData = effectiveData;
                    this.authenticationProviderType.set("value", actualData.type);
                    this.authenticationProviderName.set("value", actualData.name);
                    this.authenticationProviderType.set("disabled", true);
                    this.authenticationProviderName.set("disabled", true);
                }
                else
                {
                    this.authenticationProviderType.set("disabled", false);
                    this.authenticationProviderName.set("disabled", false);
                    this.initialData = {};
                    this.effectiveData = {};
                }

                this.dialog.show();
                if (!this.resizeEventRegistered)
                {
                    this.resizeEventRegistered = true;
                    util.resizeContentAreaAndRepositionDialog(dom.byId("addAuthenticationProvider.contentPane"), this.dialog);
                }
            },
            _cancel: function(e)
            {
                event.stop(e);
                this.dialog.hide();
            },
            _add: function(e)
            {
                event.stop(e);
                this._submit();
            },
            _submit: function()
            {
                if(this.authenticationProviderForm.validate() && this.preferencesProviderForm.validate())
                {
                    var success = false,failureReason=null;

                    var authenticationProviderData = util.getFormWidgetValues(this.authenticationProviderForm, this.initialData);

                    var encodedAuthenticationProviderName = encodeURIComponent(this.authenticationProviderName.value);
                    xhr.put({
                        url: "api/latest/authenticationprovider/" + encodedAuthenticationProviderName,
                        sync: true,
                        handleAs: "json",
                        headers: { "Content-Type": "application/json"},
                        putData: json.stringify(authenticationProviderData),
                        load: function(x) {success = true; },
                        error: function(error) {success = false; failureReason = error;}
                    });

                    if(success === true)
                    {
                        var preferencesProviderResult = this.preferencesProviderForm.submit(encodedAuthenticationProviderName);
                        success = preferencesProviderResult.success;
                        failureReason = preferencesProviderResult.failureReason;
                    }

                    if (success == true)
                    {
                        this.dialog.hide();
                    }
                    else
                    {
                        util.xhrErrorHandler(failureReason);
                    }
                }
                else
                {
                    alert('Form contains invalid data. Please correct first');
                }
            },
            _authenticationProviderTypeChanged: function(type)
            {
                this._typeChanged(type, this.authenticationProviderTypeFieldsContainer, "qpid/management/authenticationprovider/", "AuthenticationProvider" );
            },
            _typeChanged: function(type, typeFieldsContainer, baseUrl, category )
            {
                var widgets = registry.findWidgets(typeFieldsContainer);
                array.forEach(widgets, function(item) { item.destroyRecursive();});
                construct.empty(typeFieldsContainer);
                this.preferencesProviderForm.set("disabled", !type || !util.supportsPreferencesProvider(type));
                if (type)
                {
                    var that = this;
                    require([ baseUrl + type.toLowerCase() + "/add"], function(typeUI)
                    {
                        try
                        {
                            typeUI.show({containerNode:typeFieldsContainer, parent: that, data: that.initialData, effectiveData: that.effectiveData});
                            util.applyMetadataToWidgets(typeFieldsContainer, category, type);
                        }
                        catch(e)
                        {
                            console.warn(e);
                        }
                    });
                }
            }
        };

        try
        {
            addAuthenticationProvider.init();
        }
        catch(e)
        {
            console.warn(e);
        }
        return addAuthenticationProvider;
    }

);
