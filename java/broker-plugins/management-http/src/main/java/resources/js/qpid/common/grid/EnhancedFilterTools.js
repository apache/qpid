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
  "dojo/_base/event",
  "dijit/form/Button",
  "dijit/form/ToggleButton",
  "qpid/common/grid/RowNumberLimitDialog",
  "qpid/common/grid/ColumnDefDialog",
  "qpid/common/grid/FilterSummary"
], function(declare, event, Button, ToggleButton, RowNumberLimitDialog, ColumnDefDialog, FilterSummary){

  var _stopEvent = function (evt){
    try{
      if(evt && evt.preventDefault){
        event.stop(evt);
      }
      }catch(e){}
  };

  return declare("qpid.common.grid.EnhancedFilterTools", null, {

    grid: null,
    filterBar: null,
    filterStatusTip: null,
    clearFilterDialog: null,
    filterDefDialog: null,

    columnDefDialog: null,
    columnDefButton: null,
    filterDefButton: null,
    clearFilterButton: null,
    filterSummary: null,
    setRowNumberLimitButton: null,
    setRowNumberLimitDialog: null,
    refreshButton: null,
    autoRefreshButton: null,

    constructor: function(params)
    {
      this.inherited(arguments);

      this.filterBar = params.toolbar;
      this.grid = params.grid;
      this.filterStatusTip= params.filterStatusTip;
      this.clearFilterDialog = params.clearFilterDialog;
      this.filterDefDialog = params.filterDefDialog;
      this.ruleCountToConfirmClearFilter = params.ruleCountToConfirmClearFilter;

      this._addRefreshButtons();
      this._addRowLimitButton(params.defaulGridRowLimit);
      this._addColumnsButton();

      if (!params.disableFiltering)
      {
          this._addFilteringTools(params.nls);
      }
    },

    toggleClearFilterBtn: function(clearFlag)
    {
      var filterLayer = this.grid.layer("filter");
      var filterSet = filterLayer && filterLayer.filterDef && filterLayer.filterDef();
      this.clearFilterButton.set("disabled", !filterSet);
    },

    destroy: function()
    {
      this.inherited(arguments);

      if (this.columnDefDialog)
      {
        this.columnDefDialog.destroy();
        this.columnDefDialog = null;
      }
      if (this.columnDefButton)
      {
        this.columnDefButton.destroy();
        this.columnDefButton = null;
      }
      if (this.filterDefButton)
      {
        this.filterDefButton.destroy();
        this.filterDefButton = null;
      }
      if (this.clearFilterButton)
      {
        this.clearFilterButton.destroy();
        this.clearFilterButton = null;
      }
      if (this.filterSummary)
      {
        this.filterSummary.destroy();
        this.filterSummary = null;
      }
      if (this.setRowNumberLimitButton)
      {
        this.setRowNumberLimitButton.destroy();
        this.setRowNumberLimitButton = null;
      }
      if (this.setRowNumberLimitDialog)
      {
        this.setRowNumberLimitDialog.destroy();
        this.setRowNumberLimitDialog = null;
      }
      if (this.refreshButton)
      {
        this.refreshButton.destroy();
        this.refreshButton = null;
      }
      if (this.autoRefreshButton)
      {
        this.autoRefreshButton.destroy();
        this.autoRefreshButton = null;
      }

      this.grid = null;
      this.filterBar = null;
      this.filterStatusTip = null;
      this.clearFilterDialog = null;
      this.filterDefDialog = null;
    },

    _addRefreshButtons: function()
    {
      var self = this;
      this.refreshButton = new dijit.form.Button({
        label: "Refresh",
        type: "button",
        iconClass: "gridRefreshIcon",
        title: "Manual Refresh"
      });

      this.autoRefreshButton = new dijit.form.ToggleButton({
        label: "Auto Refresh",
        type: "button",
        iconClass: "gridAutoRefreshIcon",
        title: "Auto Refresh"
      });

      this.autoRefreshButton.on("change", function(value){
          self.grid.updater.updatable=value;
          self.refreshButton.set("disabled", value);
      });

      this.refreshButton.on("click", function(value){
          self.grid.updater.performUpdate();
      });

      this.filterBar.addChild(this.autoRefreshButton);
      this.filterBar.addChild(this.refreshButton);
    },

    _addRowLimitButton: function(defaulGridRowLimit)
    {
      var self = this;
      this.setRowNumberLimitButton = new dijit.form.Button({
        label: "Set Row Limit",
        type: "button",
        iconClass: "rowNumberLimitIcon",
        title: "Set Row Number Limit"
      });
      this.setRowNumberLimitButton.set("title", "Set Row Number Limit (Current: " + defaulGridRowLimit +")");

      this.setRowNumberLimitDialog = new RowNumberLimitDialog(this.grid.domNode, function(newLimit){
        if (newLimit > 0 && self.grid.updater.appendLimit != newLimit )
        {
          self.grid.updater.appendLimit = newLimit;
          self.grid.updater.performRefresh([]);
          self.setRowNumberLimitButton.set("title", "Set Row Number Limit (Current: " + newLimit +")");
        }
      });

      this.setRowNumberLimitButton.on("click", function(evt){
        self.setRowNumberLimitDialog.showDialog(self.grid.updater.appendLimit);
      });

      this.filterBar.addChild(this.setRowNumberLimitButton);
    },

    _addColumnsButton: function()
    {
      var self = this;
      this.columnDefDialog = new ColumnDefDialog({grid: this.grid});

      this.columnDefButton = new dijit.form.Button({
        label: "Display Columns",
        type: "button",
        iconClass: "columnDefDialogButtonIcon",
        title: "Show/Hide Columns"
      });

      this.columnDefButton.on("click", function(e){
        _stopEvent(e);
        self.columnDefDialog.showDialog();
      });

      this.filterBar.addChild(this.columnDefButton);
    },

    _addFilteringTools: function(nls)
    {
      var self = this;

      this.filterDefButton = new dijit.form.Button({
        "class": "dojoxGridFBarBtn",
        label: "Set Filter",
        iconClass: "dojoxGridFBarDefFilterBtnIcon",
        showLabel: "true",
        title: "Define filter"
      });

      this.clearFilterButton = new dijit.form.Button({
        "class": "dojoxGridFBarBtn",
        label: "Clear filter",
        iconClass: "dojoxGridFBarClearFilterButtontnIcon",
        showLabel: "true",
        title: "Clear filter",
        disabled: true
      });


      this.filterDefButton.on("click", function(e){
        _stopEvent(e);
        self.filterDefDialog.showDialog();
      });

      this.clearFilterButton.on("click", function(e){
        _stopEvent(e);
        if (self.ruleCountToConfirmClearFilter && self.filterDefDialog.getCriteria() >= self.ruleCountToConfirmClearFilter)
        {
          self.clearFilterDialog.show();
        }
        else
        {
          self.grid.layer("filter").filterDef(null);
          self.toggleClearFilterBtn(true)
        }
      });

      this.filterSummary = new FilterSummary({grid: this.grid, filterStatusTip: this.filterStatusTip, nls: nls});

      this.filterBar.addChild(this.filterDefButton);
      this.filterBar.addChild(this.clearFilterButton);

      this.filterBar.addChild(new dijit.ToolbarSeparator());
      this.filterBar.addChild(this.filterSummary, "last");
      this.filterBar.getColumnIdx = function(coordX){return self.filterSummary._getColumnIdx(coordX);};

    }
  });
});