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
        "dojo/_base/array",
        "dojo/dom-construct",
        "dojo/parser",
        "dojo/query",
        "dojo/store/Memory",
        "dijit/_WidgetBase",
        "dijit/registry",
        "dojo/text!common/TimeZoneSelector.html",
        "qpid/common/timezone",
        "dijit/form/ComboBox",
        "dijit/form/FilteringSelect",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
function (declare, array, domConstruct, parser, query, Memory, _WidgetBase, registry, template, timezone) {

  var preferencesRegions = ["Africa","America","Antarctica","Arctic","Asia","Atlantic","Australia","Europe","Indian","Pacific"];

  function initSupportedRegions()
  {
    var supportedRegions = [{"id": "undefined", "name": "Undefined"}];
    for(var j = 0; j<preferencesRegions.length; j++)
    {
      supportedRegions.push({id: preferencesRegions[j], name: preferencesRegions[j] });
    }
    return supportedRegions;
  }

  return declare("qpid.common.TimeZoneSelector", [_WidgetBase], {

    value: null,
    domNode: null,
    _regionSelector: null,
    _citySelector: null,

    constructor: function(args)
    {
      this._args = args;
    },

    buildRendering: function(){
      this.domNode = domConstruct.create("div", {innerHTML: template});
      parser.parse(this.domNode);
    },

    postCreate: function(){
      this.inherited(arguments);

      var supportedTimeZones = timezone.getAllTimeZones();

      this._citySelector = registry.byNode(query(".timezoneCity", this.domNode)[0]);
      this._citySelector.set("searchAttr", "city");
      this._citySelector.set("query", {region: /.*/});
      this._citySelector.set("labelAttr", "city");
      this._citySelector.set("store", new Memory({ data: supportedTimeZones }));
      if (this._args.name)
      {
        this._citySelector.set("name", this._args.name);
      }
      this._regionSelector = registry.byNode(query(".timezoneRegion", this.domNode)[0]);
      var supportedRegions = initSupportedRegions();
      this._regionSelector.set("store", new Memory({ data: supportedRegions }));
      var self = this;

      this._regionSelector.on("change", function(value){
        if (value=="undefined")
        {
          self._citySelector.set("disabled", true);
          self._citySelector.query.region = /.*/;
          self.value = null;
          self._citySelector.set("value", null);
        }
        else
        {
          self._citySelector.set("disabled", false);
          self._citySelector.query.region = value || /.*/;
          if (this.timeZone)
          {
            self._citySelector.set("value", this.timeZone);
            this.timeZone = null;
          }
          else
          {
            self._citySelector.set("value", null);
          }
        }
      });

      this._citySelector.on("change", function(value){
        self.value = value;
      });

      this._setValueAttr(this._args.value);
    },

    _setValueAttr: function(value)
    {
      if (value)
      {
        var elements = value.split("/");
        if (elements.length > 1)
        {
          this._regionSelector.timeZone = value;
          this._regionSelector.set("value", elements[0]);
          this._citySelector.set("value", value);
        }
        else
        {
          this._regionSelector.set("value", "undefined");
        }
      }
      else
      {
        this._regionSelector.set("value", "undefined");
      }
      this.value = value;
    },

    destroy: function()
    {
      if (this.domNode)
      {
        this.domNode.destroy();
        this.domNode = null;
      }
      _regionSelector: null;
      _citySelector: null;
    }

  });
});