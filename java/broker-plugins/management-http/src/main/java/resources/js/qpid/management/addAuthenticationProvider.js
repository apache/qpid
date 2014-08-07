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
        'dojo/_base/json',
        "dojo/store/Memory",
        "dijit/form/FilteringSelect",
        "dojo/_base/connect",
        "dojo/dom-style",
        "qpid/management/PreferencesProviderFields",
        "qpid/common/util",
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
    function (xhr, dom, construct, win, registry, parser, array, event, json, Memory, FilteringSelect, connect, domStyle, PreferencesProviderFields, util) {

        var addAuthenticationProvider = {};

        var node = construct.create("div", null, win.body(), "last");

        var convertToAuthenticationProvider = function convertToAuthenticationProvider(formValues)
        {
            var newProvider = {};

            newProvider.name = dijit.byId("formAddAuthenticationProvider.name").value;
            newProvider.type = dijit.byId("authenticationProviderType").value;
            var id = dojo.byId("formAddAuthenticationProvider.id").value;
            if (id)
            {
                newProvider.id = id;
            }
            for(var propName in formValues)
            {
                if(formValues.hasOwnProperty(propName))
                {
                    if (propName.indexOf("preferencesProvider") == 0)
                    {
                      continue;
                    }
                    if(formValues[ propName ] !== "") {
                        newProvider[ propName ] = formValues[propName];
                    }

                }
            }
            return newProvider;
        }

        var showFieldSets = function showFieldSets(providerType, fieldSets)
        {
            for(var key in fieldSets)
            {
                var layout = fieldSets[key];
                var disabled = key != providerType;
                var displayValue = key == providerType ? "block" : "none";
                var widgets = layout.getDescendants();
                array.forEach(widgets, function(widget)
                {
                    widget.set("disabled", disabled);
                });

                domStyle.set(fieldSets[key].domNode, "display", displayValue);
            }
            if (fieldSets[providerType])
            {
                fieldSets[providerType].getParent().resize();
            }
        }

        var getAuthenticationProviderWidgetId = function getAuthenticationProviderWidgetId(providerType, attribute)
        {
            return "ap_" + providerType + "Field" + attribute;
        }

        var showPreferencesProviderFields = function showPreferencesProviderFields(provider)
        {
          var preferencesProviderDiv = dojo.byId("addAuthenticationProvider.preferencesProvider");
          var preferencesProviderPanel = dijit.byId("addAuthenticationProvider.preferencesProviderPanel");
          if (provider && provider.type == "Anonymous")
          {
            preferencesProviderPanel.domNode.style.display = 'none';
          }
          else
          {
            preferencesProviderPanel.domNode.style.display = 'block';
            PreferencesProviderFields.show(preferencesProviderDiv, provider && provider.preferencesproviders ? provider.preferencesproviders[0] : null);
          }
        }

        var loadProviderAndDisplayForm = function loadProviderAndDisplayForm(providerName, dialog)
        {
            if (providerName)
            {
                xhr.get({
                    url: "api/latest/authenticationprovider/" + encodeURIComponent(providerName),
                    content: { actuals: true },
                    handleAs: "json"
                }).then(
                   function(data) {
                       var provider = data[0];
                       var providerType = provider.type;
                       var nameField = dijit.byId("formAddAuthenticationProvider.name");
                       nameField.set("value", provider.name);
                       nameField.set("disabled", true);
                       nameField.set("regExpGen", util.nameOrContextVarRegexp);
                       dialog.providerChooser.set("value", providerType);
                       dialog.providerChooser.set("disabled", true);
                       dojo.byId("formAddAuthenticationProvider.id").value=provider.id;
                       for(var attribute in provider)
                       {
                           if (provider.hasOwnProperty(attribute))
                           {
                               var widject = dijit.byId(getAuthenticationProviderWidgetId(providerType, attribute));
                               if (widject)
                               {
                                   widject.set("value", provider[attribute]);
                               }
                           }
                       }
                       showPreferencesProviderFields(provider);
                       registry.byId("addAuthenticationProvider").show();
               });
            }
            else
            {
                showPreferencesProviderFields();
                registry.byId("addAuthenticationProvider").show();
            }
        }

        xhr.get({url: "addAuthenticationProvider.html",
                 sync: true,
                 load:  function(data) {
                            var theForm;
                            node.innerHTML = data;
                            addAuthenticationProvider.dialogNode = dom.byId("addAuthenticationProvider");
                            parser.instantiate([addAuthenticationProvider.dialogNode]);
                            theForm = registry.byId("formAddAuthenticationProvider");
                            theForm.on("submit", function(e) {

                                event.stop(e);
                                if(theForm.validate()){

                                    var newAuthenticationManager = convertToAuthenticationProvider(theForm.getValues());
                                    var that = this;

                                    xhr.put({url: "api/latest/authenticationprovider/" + encodeURIComponent(newAuthenticationManager.name),
                                             sync: true, handleAs: "json",
                                             headers: { "Content-Type": "application/json"},
                                             putData: json.toJson(newAuthenticationManager),
                                             load: function(x) {that.success = true; },
                                             error: function(error) {that.success = false; that.failureReason = error;}});

                                    if(this.success === true)
                                    {
                                      if (PreferencesProviderFields.save(newAuthenticationManager.name))
                                      {
                                        registry.byId("addAuthenticationProvider").hide();
                                      }
                                    }
                                    else
                                    {
                                        alert("Authentication Provider Error:" + this.failureReason);
                                    }
                                    return false;
                                }else{
                                    alert('Form contains invalid data.  Please correct first');
                                    return false;
                                }
                            });
                        }});

        addAuthenticationProvider.show = function(providerName) {
            var that = this;
            registry.byId("formAddAuthenticationProvider").reset();
            dojo.byId("formAddAuthenticationProvider.id").value="";
            registry.byId("formAddAuthenticationProvider.name").set("disabled", false);
            if (this.providerChooser)
            {
                this.providerChooser.set("disabled", false);
            }

            if (!that.hasOwnProperty("providerFieldSets"))
            {
                xhr.get({
                    url: "service/helper?action=ListAuthenticationProviderAttributes",
                    handleAs: "json"
                }).then(
                   function(data) {
                       var providers =  [];
                       var providerIndex = 0;
                       that.providerFieldSetsContainer = dom.byId("addAuthenticationProvider.fieldSets");
                       that.providerFieldSets = [];

                       for (var providerType in data) {
                           if (data.hasOwnProperty(providerType)) {
                               providers[providerIndex++] = {id: providerType, name: providerType};

                               var attributes = data[providerType].attributes;
                               var resources = data[providerType].descriptions;
                               var layout = new dojox.layout.TableContainer( {
                                   id: providerType + "FieldSet",
                                   cols: 1,
                                   "labelWidth": "200",
                                   showLabels: true,
                                   orientation: "horiz"
                               });
                               for(var i=0; i < attributes.length; i++) {
                                   if ("type" == attributes[i])
                                   {
                                       continue;
                                   }
                                   var labelValue = attributes[i];
                                   if (resources && resources[attributes[i]])
                                   {
                                       labelValue = resources[attributes[i]];
                                   }
                                   var text = new dijit.form.TextBox({
                                       label: labelValue + ":",
                                       id: getAuthenticationProviderWidgetId(providerType, attributes[i]),
                                       name: attributes[i]
                                   });
                                   layout.addChild(text);
                               }
                               layout.placeAt("addAuthenticationProvider.fieldSets");
                               that.providerFieldSets[providerType]=layout;
                               layout.startup();
                           }
                       }

                       var providersStore = new Memory({ data: providers });
                       if(that.providerChooser) {
                           that.providerChooser.destroy( false );
                       }

                       var providersDiv = dom.byId("addAuthenticationProvider.selectAuthenticationProviderDiv");
                       var input = construct.create("input", {id: "addAuthenticationProviderType"}, providersDiv);

                       that.providerChooser = new FilteringSelect({ id: "authenticationProviderType",
                                                                 name: "type",
                                                                 store: providersStore,
                                                                 searchAttr: "name"}, input);
                       that.providerChooser.on("change",
                           function(value)
                           {
                               dijit.byId("addAuthenticationProvider.preferencesProviderPanel").domNode.style.display = (value == "Anonymous" ? "none" : "block");
                               showFieldSets(that.providerChooser.value, that.providerFieldSets);
                           }
                       );
                       var providerType = providers[0].name;
                       that.providerChooser.set("value", providerType);
                       showFieldSets(providerType, that.providerFieldSets);
                       loadProviderAndDisplayForm(providerName, that)
               });
            }
            else
            {
                loadProviderAndDisplayForm(providerName, that);
            }
        }

        return addAuthenticationProvider;
    });