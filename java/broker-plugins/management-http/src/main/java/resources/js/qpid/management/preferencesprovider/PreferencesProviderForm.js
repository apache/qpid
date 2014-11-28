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
        "qpid/common/util",
        "qpid/common/metadata",
        "dojo/_base/xhr",
        "dojo/_base/declare",
        "dojo/_base/array",
        "dojo/dom-construct",
        "dojo/_base/window",
        "dojo/query",
        "dojo/json",
        "dijit/_WidgetBase",
        "dijit/_OnDijitClickMixin",
        "dijit/_TemplatedMixin",
        "dijit/_WidgetsInTemplateMixin",
        "dijit/registry",
        "dojo/text!preferencesprovider/preferencesProviderForm.html",
        "dojox/html/entities",
        "dijit/form/ValidationTextBox",
        "dijit/form/FilteringSelect",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
function (util, metadata, xhr, declare, array, domConstruct, win, query, json, _WidgetBase,
            _OnDijitClickMixin, _TemplatedMixin, _WidgetsInTemplateMixin, registry, template, entities)
 {

  return declare("qpid.preferencesprovider.PreferencesProviderForm",
                    [_WidgetBase, _OnDijitClickMixin, _TemplatedMixin, _WidgetsInTemplateMixin], {

    templateString: template,
    domNode: null,
    preferencesProviderForm : null,
    preferencesProviderNameWidget : null,
    preferencesProviderTypeWidget : null,
    preferencesProviderTypeFieldsContainer: null,

    buildRendering: function()
    {
        //Strip out the apache comment header from the template html as comments unsupported.
        this.templateString = this.templateString.replace(/<!--[\s\S]*?-->/g, "");
        this.inherited(arguments);
    },
    postCreate: function()
    {
        this.inherited(arguments);
        var that = this;

        this.preferencesProviderNameWidget.set("regExpGen", util.nameOrContextVarRegexp);

        var supportedPreferencesProviderTypes = metadata.getTypesForCategory("PreferencesProvider");
        supportedPreferencesProviderTypes.sort();
        supportedPreferencesProviderTypes.splice(0,0,"None");
        var preferencesProviderTypeStore = util.makeTypeStore(supportedPreferencesProviderTypes);
        this.preferencesProviderTypeWidget.set("store", preferencesProviderTypeStore);
        this.preferencesProviderTypeWidget.on("change", function(type){that._preferencesProviderTypeChanged(type);});
        this.preferencesProviderForm.on("submit", function() { return false; })
    },
    reset: function()
    {
        this.data = null;
        this.preferencesProviderForm.reset();
        this.preferencesProviderTypeWidget.set("value", "None");
    },
    submit: function(encodedAuthenticationProviderName)
    {
        if (this.preferencesProviderTypeWidget.get("value") != "None")
        {
            var preferencesProviderData = util.getFormWidgetValues(this.preferencesProviderForm); // TODO initialValues
            var encodedPreferencesProviderName = encodeURIComponent(preferencesProviderData.name);
            var success = false;
            var failureReason = null;
            xhr.put({
                url: "api/latest/preferencesprovider/" + encodedAuthenticationProviderName + "/" + encodedPreferencesProviderName,
                sync: true,
                handleAs: "json",
                headers: { "Content-Type": "application/json"},
                putData: json.stringify(preferencesProviderData),
                load: function (x) { success = true; },
                error: function (error) { success = false; failureReason = error; }
            });
            return { success: success, failureReason: failureReason };
        }
        return { success: true, failureReason: null };
    },
    validate: function()
    {
        return this.preferencesProviderForm.validate();
    },
    load: function(authenticationProviderName, providerName)
    {
        var that = this;
        xhr.get({
            url: "api/latest/preferencesprovider/" + encodeURIComponent(authenticationProviderName) + "/" + encodeURIComponent(providerName),
            handleAs: "json"
        }).then(function(data){that._load(data[0])});
    },
    _load:function(data)
    {
        this.data = data;
        this.preferencesProviderNameWidget.set("value", data.name);
        if (data.type == this.preferencesProviderTypeWidget.get("value"))
        {
            this._toggleWidgets(data.type);
        }
        else
        {
            this.preferencesProviderTypeWidget.set("value", data.type);
        }
    },
    _preferencesProviderTypeChanged: function(type)
    {
        var typeFieldsContainer = this.preferencesProviderTypeFieldsContainer;
        var widgets = registry.findWidgets(typeFieldsContainer);
        array.forEach(widgets, function(item) { item.destroyRecursive();});
        domConstruct.empty(typeFieldsContainer);
        this._toggleWidgets(type);
        if (type)
        {
            if (type == "None")
            {
                this.preferencesProviderNameWidget.set("value", "");
            }
            else
            {
                var that = this;
                require([ "qpid/management/preferencesprovider/" + type.toLowerCase() + "/add"], function(typeUI)
                {
                    try
                    {
                        typeUI.show({containerNode:typeFieldsContainer, parent: that, data: that.data});
                        util.applyMetadataToWidgets(typeFieldsContainer, "PreferencesProvider", type);
                    }
                    catch(e)
                    {
                        console.warn(e);
                    }
                });
            }
        }
    },
    _toggleWidgets: function(type)
    {
        if (this.disabled)
        {
            this.preferencesProviderNameWidget.set("disabled", true);
            this.preferencesProviderTypeWidget.set("disabled", true);
        }
        else
        {
            if (this.data)
            {
                // editing
                this.preferencesProviderNameWidget.set("disabled", true);
                this.preferencesProviderTypeWidget.set("disabled", true);
            }
            else
            {
                this.preferencesProviderNameWidget.set("disabled", !type || type == "None");
                this.preferencesProviderTypeWidget.set("disabled", false);
            }
        }
    },
    _setDisabledAttr: function(disabled)
    {
        this.inherited(arguments);
        this.disabled = disabled;
        this.reset();
    },
  });
});
