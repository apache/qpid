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

require(["dijit/form/DropDownButton",
         "dijit/TooltipDialog",
         "dijit/form/TextBox",
         "dojo/_base/xhr",
         "dojo/dom",
         "dojo/dom-construct",
         "qpid/authorization/sasl",
         "dojo/domReady!"], function(DropDownButton, TooltipDialog, TextBox, xhr, dom, domConstruct, sasl){

var dialog = new TooltipDialog({
    content:
        '<strong><label for="username" style="display:inline-block;width:100px;">Username:</label></strong>' +
        '<div data-dojo-type="dijit.form.TextBox" id="username"></div><br/>' +
            '<strong><label for="pass" style="display:inline-block;width:100px;">Password:</label></strong>' +
            '<div data-dojo-type="dijit.form.TextBox" type="password" id="pass"></div><br/>' +
        '<button data-dojo-type="dijit.form.Button" type="submit" id="loginButton">Login</button>'
});

var button = new DropDownButton({
    label: "Login",
    dropDown: dialog
});

var usernameSpan = domConstruct.create("span", {
    innerHTML: '<strong>User: </strong> <span id="authenticatedUser"></span><a href="logout">[logout]</a>',
    style: { display: "none" }
});


var loginDiv = dom.byId("login");
loginDiv.appendChild(usernameSpan);
loginDiv.appendChild(button.domNode);

var updateUI = function updateUI(data)
{
    if(data.user)
    {
        dojo.byId("authenticatedUser").innerHTML = data.user;
        dojo.style(button.domNode, {display: 'none'});
        dojo.style(usernameSpan, {display: 'block'});
    }
    else
    {
        dojo.style(button.domNode, {display: 'block'});
        dojo.style(usernameSpan, {display: 'none'});
    }
};

dijit.byId("loginButton").on("click", function(){
    sasl.authenticate(dojo.byId("username").value, dojo.byId("pass").value, updateUI);
});

dialog.startup();

sasl.getUser(updateUI);

});