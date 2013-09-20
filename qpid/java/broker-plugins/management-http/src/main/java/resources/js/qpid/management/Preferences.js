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
        "dojo/_base/declare",
        "dojo/_base/xhr",
        "dojo/_base/event",
        "dojo/_base/connect",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/parser",
        "dojo/json",
        "dojo/store/Memory",
        "dojo/data/ObjectStore",
        "dojox/html/entities",
        "dijit/registry",
        "qpid/common/TimeZoneSelector",
        "dojo/text!../../showPreferences.html",
        "qpid/common/util",
        "dijit/Dialog",
        "dijit/form/NumberSpinner",
        "dijit/form/CheckBox",
        "dijit/form/Textarea",
        "dijit/form/FilteringSelect",
        "dijit/form/TextBox",
        "dijit/form/DropDownButton",
        "dijit/form/Button",
        "dijit/form/Form",
        "dijit/layout/TabContainer",
        "dijit/layout/ContentPane",
        "dojox/grid/EnhancedGrid",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
function (declare, xhr, event, connect, dom, domConstruct, parser, json, Memory, ObjectStore, entities, registry, TimeZoneSelector, markup, util) {

  var preferenceNames = ["timeZone", "updatePeriod", "saveTabs"];

  return declare("qpid.management.Preferences", null, {

    preferencesDialog: null,
    saveButton: null,
    cancelButton: null,

    constructor: function()
    {
      var that = this;

      this.domNode = domConstruct.create("div", {innerHTML: markup});
      this.preferencesDialog = parser.parse(this.domNode)[0];

      for(var i=0; i<preferenceNames.length; i++)
      {
        var name = preferenceNames[i];
        this[name] = registry.byId("preferences." + name);
      }

      this.saveButton = registry.byId("preferences.saveButton");
      this.cancelButton = registry.byId("preferences.cancelButton");
      this.theForm = registry.byId("preferences.preferencesForm");
      this.users = registry.byId("preferences.users");
      this.users.set("structure", [ { name: "User", field: "name", width: "50%"},
                                 { name: "Authentication Provider", field: "authenticationProvider", width: "50%"}]);
      this.cancelButton.on("click", function(){that.preferencesDialog.hide();});
      this.deletePreferencesButton = registry.byId("preferences.deletePreeferencesButton");
      this.deletePreferencesButton.on("click", function(){
         if (util.deleteGridSelections(
            null,
            that.users,
            "rest/userpreferences",
            "Are you sure you want to delete preferences for user",
            "user"))
          {
             that._updateUsersWithPreferences();
          }
      });
      var deletePreferencesButtonToggler = function(rowIndex){
        var data = that.users.selection.getSelected();
        that.deletePreferencesButton.set("disabled",!data.length );
      };
      connect.connect(this.users.selection, 'onSelected',  deletePreferencesButtonToggler);
      connect.connect(this.users.selection, 'onDeselected',  deletePreferencesButtonToggler);
      this.theForm.on("submit", function(e){
        event.stop(e);
        if(that.theForm.validate()){
          var preferences = {};
          for(var i=0; i<preferenceNames.length; i++)
          {
            var name = preferenceNames[i];
            var preferenceWidget = that[name];
            if (preferenceWidget)
            {
              preferences[name] = preferenceWidget.get("value");
            }
          }
          xhr.post({
                url: "rest/preferences",
                sync: true,
                handleAs: "json",
                headers: { "Content-Type": "application/json"},
                postData: json.stringify(preferences),
                load: function(x) {that.success = true; },
                error: function(error) {that.success = false; that.failureReason = error;}
          });
          if(that.success === true)
          {
            that.preferencesDialog.hide();
          }
          else
          {
            alert("Error:" + that.failureReason);
          }
        }
        return false;
      });
      this.preferencesDialog.startup();
    },

    showDialog: function(){
      var that = this;
      xhr.get({
        url: "rest/preferences",
        sync: true,
        handleAs: "json",
        load: function(data) {
          that._updatePreferencesWidgets(data);
          that._updateUsersWithPreferences();
          that.preferencesDialog.show();
       },
       error: function(error){
         alert("Cannot load user preferences : " + error);
       }
      });
    },

    destroy: function()
    {
      if (this.preferencesDialog)
      {
        this.preferencesDialog.destroyRecursevly();
        this.preferencesDialog = null;
      }
    },

    _updatePreferencesWidgets: function(data)
    {
      for(var i=0; i<preferenceNames.length; i++)
      {
        var preference = preferenceNames[i];
        if (this.hasOwnProperty(preference))
        {
          var value = data ? data[preference] : null;
          if (typeof value == "string")
          {
            value = entities.encode(String(value))
          }
          this[preference].set("value", value);
        }
      }
    },

    _updateUsersWithPreferences: function()
    {
      var that = this;
      xhr.get({
        url: "rest/userpreferences",
        sync: false,
        handleAs: "json"
      }).then(
         function(users) {
             for(var i=0; i<users.length; i++)
             {
               users[i].id = users[i].authenticationProvider + "/" + users[i].name;
             }
             var usersStore = new Memory({data: users, idProperty: "id"});
             var usersDataStore = new ObjectStore({objectStore: usersStore});
             if (that.users.store)
             {
               that.users.store.close();
             }
             that.users.set("store", usersDataStore);
             that.users._refresh();
      });
    }

  });
});