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
define(["dojo/_base/lang",
        "dojo/_base/xhr",
        "dojo/dom",
        "dojo/dom-construct",
        "dijit/registry",
        "dojo/parser",
        "dojo/_base/array",
        "dojo/_base/event",
        'dojo/json',
        "qpid/common/util",
        "qpid/common/metadata",
        "dojo/text!addAccessControlProvider.html",
        "dojo/store/Memory",
        "dojox/validate/us",
        "dojox/validate/web",
        "dijit/Dialog",
        "dijit/form/CheckBox",
        "dijit/form/Textarea",
        "dijit/form/ComboBox",
        "dijit/form/TextBox",
        "dijit/form/ValidationTextBox",
        "dijit/form/Button",
        "dijit/form/Form",
        "dijit/layout/ContentPane",
        "dojox/layout/TableContainer",
        "dojo/domReady!"],
    function (lang, xhr, dom, construct, registry, parser, array, event, json, util, metadata, template)
    {

        var addAccessControlProvider =
        {
            init: function()
            {
                var that=this;
                this.containerNode = construct.create("div", {innerHTML: template});
                parser.parse(this.containerNode).then(function(instances){  that._postParse(); });
            },
            _postParse: function()
            {
                var that=this;
                this.accessControlProviderName = registry.byId("addAccessControlProvider.name");
                this.accessControlProviderName.set("regExpGen", util.nameOrContextVarRegexp);

                this.dialog = registry.byId("addAccessControlProvider");
                this.addButton = registry.byId("addAccessControlProvider.addButton");
                this.cancelButton = registry.byId("addAccessControlProvider.cancelButton");
                this.cancelButton.on("click", function(e){that._cancel(e);});
                this.addButton.on("click", function(e){that._add(e);});

                this.accessControlProviderTypeFieldsContainer = dom.byId("addAccessControlProvider.typeFields");
                this.accessControlProviderForm = registry.byId("addAccessControlProvider.form");
                this.accessControlProviderType = registry.byId("addAccessControlProvider.type");
                this.supportedAccessControlProviderTypes = metadata.getTypesForCategory("AccessControlProvider");
                this.supportedAccessControlProviderTypes.sort();
                var accessControlProviderTypeStore = util.makeTypeStore(this.supportedAccessControlProviderTypes);
                this.accessControlProviderType.set("store", accessControlProviderTypeStore);
                this.accessControlProviderType.on("change", function(type){that._accessControlProviderTypeChanged(type);});
            },
            show: function(effectiveData)
            {
                this.accessControlProviderForm.reset();
                this.dialog.show();
            },
            _cancel: function(e)
            {
                event.stop(e);
                if (this.reader)
                {
                    this.reader.abort();
                }
                this.dialog.hide();
            },
            _add: function(e)
            {
                event.stop(e);
                this._submit();
            },
            _submit: function()
            {
                if (this.accessControlProviderForm.validate())
                {
                    var accessControlProviderData = util.getFormWidgetValues(this.accessControlProviderForm, this.initialData);
                    var encodedAccessControlProviderName = encodeURIComponent(this.accessControlProviderName.value);
                    var that = this;
                    util.post("api/latest/accesscontrolprovider/" + encodedAccessControlProviderName,
                              accessControlProviderData, function(x){that.dialog.hide();});
                }
                else
                {
                    alert('Form contains invalid data. Please correct first');
                }
            },
            _accessControlProviderTypeChanged: function(type)
            {
                this._typeChanged(type, this.accessControlProviderTypeFieldsContainer, "qpid/management/accesscontrolprovider/", "AccessControlProvider" );
            },
            _typeChanged: function(type, typeFieldsContainer, baseUrl, category )
            {
                 var widgets = registry.findWidgets(typeFieldsContainer);
                 array.forEach(widgets, function(item) { item.destroyRecursive();});
                 construct.empty(typeFieldsContainer);

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
            addAccessControlProvider.init();
        }
        catch(e)
        {
            console.warn(e);
        }
        return addAccessControlProvider;
    });
