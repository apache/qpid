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
define([
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
        "dojo/text!addGroupProvider.html",
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
    function (xhr, dom, construct, registry, parser, array, event, json, util, metadata, template)
    {

        var addGroupProvider =
        {
            init: function()
            {
                var that=this;
                this.containerNode = construct.create("div", {innerHTML: template});
                parser.parse(this.containerNode);

                this.groupProviderName = registry.byId("addGroupProvider.name");
                this.groupProviderName.set("regExpGen", util.nameOrContextVarRegexp);

                this.dialog = registry.byId("addGroupProvider");
                this.addButton = registry.byId("addGroupProvider.addButton");
                this.cancelButton = registry.byId("addGroupProvider.cancelButton");
                this.cancelButton.on("click", function(e){that._cancel(e);});
                this.addButton.on("click", function(e){that._add(e);});

                this.groupProviderTypeFieldsContainer = dom.byId("addGroupProvider.typeFields");
                this.groupProviderForm = registry.byId("addGroupProvider.form");

                this.groupProviderType = registry.byId("addGroupProvider.type");
                this.groupProviderType.on("change", function(type){that._groupProviderTypeChanged(type);});

                var supportedTypes = metadata.getTypesForCategory("GroupProvider");
                supportedTypes.sort();
                var supportedTypesStore = util.makeTypeStore(supportedTypes);
                this.groupProviderType.set("store", supportedTypesStore);
            },
            show: function(actualData)
            {
                this.initialData = actualData;
                this.groupProviderForm.reset();

                if (actualData)
                {
                    this._destroyTypeFields(this.containerNode);
                    this._initFields(actualData);
                }
                this.groupProviderName.set("disabled", actualData == null ? false : true);
                this.groupProviderType.set("disabled", actualData == null ? false : true);
                this.dialog.set("title", actualData == null ? "Add Group Provider" : "Edit Group Provider - " + actualData.name)
                this.dialog.show();
            },
            _initFields:function(data)
            {
                var type = data["type"];
                var attributes = metadata.getMetaData("GroupProvider", type).attributes;
                for(var name in attributes)
                {
                    var widget = registry.byId("addGroupProvider."+name);
                    if (widget)
                    {
                        widget.set("value", data[name]);
                    }
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
                if (this.groupProviderForm.validate())
                {
                    var success = false,failureReason=null;

                    var groupProviderData = util.getFormWidgetValues(this.groupProviderForm, this.initialData);
                    var encodedName = encodeURIComponent(this.groupProviderName.value);
                    var jsonString = json.stringify(groupProviderData);

                    try {
                    xhr.put(
                    {
                        url: "api/latest/groupprovider/" + encodedName,
                        sync: true,
                        handleAs: "json",
                        headers: { "Content-Type": "application/json"},
                        putData: jsonString,
                        load: function(x) {success = true; },
                        error: function(error) {success = false; failureReason = error;}
                    });
                    }
                    catch (e)
                    {
                    console.warn(e);
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
            _groupProviderTypeChanged: function(type)
            {
                 this._destroyTypeFields(this.groupProviderTypeFieldsContainer);
                 if (type)
                 {
                     var that = this;
                     require([ "qpid/management/groupprovider/" + type.toLowerCase() + "/add"], function(typeUI)
                     {
                         try
                         {
                             typeUI.show({containerNode: that.groupProviderTypeFieldsContainer, parent: that, data: that.initialData});
                             util.applyMetadataToWidgets(that.groupProviderTypeFieldsContainer, "GroupProvider", type);
                         }
                         catch(e)
                         {
                             console.warn(e);
                         }
                     });
                 }
            },
            _destroyTypeFields: function(typeFieldsContainer)
            {
                var widgets = registry.findWidgets(typeFieldsContainer);
                array.forEach(widgets, function(item) { item.destroyRecursive();});
                construct.empty(typeFieldsContainer);
            }
        };

        try
        {
            addGroupProvider.init();
        }
        catch(e)
        {
            console.warn(e);
        }
        return addGroupProvider;

    });