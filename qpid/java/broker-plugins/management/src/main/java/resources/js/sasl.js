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
require(["dijit/form/DropDownButton", "dijit/TooltipDialog", "dijit/form/TextBox",
                     "dojo/_base/xhr", "dojox/encoding/base64", "dojox/encoding/digests/_base", "dojox/encoding/digests/MD5"]);
var button;
var usernameSpan;

function encodeUTF8(str)
{
    var byteArray = [];
    for (var i = 0; i < str.length; i++)
        if (str.charCodeAt(i) <= 0x7F)
            byteArray.push(str.charCodeAt(i));
        else {
            var h = encodeURIComponent(str.charAt(i)).substr(1).split('%');
            for (var j = 0; j < h.length; j++)
                byteArray.push(parseInt(h[j], 16));
        }
    return byteArray;
};

function decodeUTF8(byteArray)
{
    var str = '';
    for (var i = 0; i < byteArray.length; i++)
        str +=  byteArray[i] <= 0x7F?
                byteArray[i] === 0x25 ? "%25" :
                String.fromCharCode(byteArray[i]) :
                "%" + byteArray[i].toString(16).toUpperCase();
    return decodeURIComponent(str);
};


function saslPlain(user, password)
{
    var responseArray = [ 0 ].concat(encodeUTF8( user )).concat( [ 0 ] ).concat( encodeUTF8( password ) );
    var plainResponse = dojox.encoding.base64.encode(responseArray);

    // Using dojo.xhrGet, as very little information is being sent
    dojo.xhrPost({
        // The URL of the request
        url: "/rest/sasl",
        content: {
            mechanism: "PLAIN",
            response: plainResponse
        },
        handleAs: "json",
        failOk: true
    }).then(function(data)
            {
                updateAuthentication();
            },
            function(error)
            {
                if(error.status == 401)
                {
                    alert("Authentication Failed");
                }
                else
                {
                    alert(error);
                }
                updateAuthentication();
            });
}

function saslCramMD5(user, password)
{

    // Using dojo.xhrGet, as very little information is being sent
    dojo.xhrPost({
        // The URL of the request
        url: "/rest/sasl",
        content: {
            mechanism: "CRAM-MD5",
        },
        handleAs: "json",
        failOk: true
    }).then(function(data)
            {

                var challengeBytes = dojox.encoding.base64.decode(data.challenge);
                var wa=[];
                var bitLength = challengeBytes.length*8;
                for(var i=0; i<bitLength; i+=8)
                {
                    wa[i>>5] |= (challengeBytes[i/8] & 0xFF)<<(i%32);
                }
                var challengeStr = dojox.encoding.digests.wordToString(wa).substring(0,challengeBytes.length);

                var digest =  user + " " + dojox.encoding.digests.MD5._hmac(challengeStr, password, dojox.encoding.digests.outputTypes.Hex);
                var id = data.id;

                var response = dojox.encoding.base64.encode(encodeUTF8( digest ));

                dojo.xhrPost({
                        // The URL of the request
                        url: "/rest/sasl",
                        content: {
                            id: id,
                            response: response
                        },
                        handleAs: "json",
                        failOk: true
                    }).then(function(data)
                                        {
                                            updateAuthentication();
                                        },
                                        function(error)
                                        {
                                            if(error.status == 401)
                                            {
                                                alert("Authentication Failed");
                                            }
                                            else
                                            {
                                                alert(error);
                                            }
                                            updateAuthentication();
                                        });

            },
            function(error)
            {
                if(error.status == 401)
                {
                    alert("Authentication Failed");
                }
                else
                {
                    alert(error);
                }
            });
}

function doAuthenticate()
{
    saslCramMD5(dojo.byId("username").value, dojo.byId("pass").value);
    updateAuthentication();
}


function updateAuthentication()
{
    dojo.xhrGet({
        // The URL of the request
        url: "/rest/sasl",
        handleAs: "json"
    }).then(function(data)
            {
                if(data.user)
                {
                    dojo.byId("authenticatedUser").innerHTML = data.user;
                    dojo.style(button.domNode, {visibility: 'hidden'});
                    dojo.style(usernameSpan, {visibility: 'visible'});
                }
                else
                {
                    dojo.style(button.domNode, {visibility: 'visible'});
                    dojo.style(usernameSpan, {visibility: 'hidden'});
                }
            }
        );
}

require(["dijit/form/DropDownButton", "dijit/TooltipDialog", "dijit/form/TextBox", "dojo/_base/xhr", "dojo/dom", "dojo/dom-construct", "dojo/domReady!"],
        function(DropDownButton, TooltipDialog, TextBox, xhr, dom, domConstruct){
    var dialog = new TooltipDialog({
        content:
            '<strong><label for="username" style="display:inline-block;width:100px;">Username:</label></strong>' +
            '<div data-dojo-type="dijit.form.TextBox" id="username"></div><br/>' +
        	'<strong><label for="pass" style="display:inline-block;width:100px;">Password:</label></strong>' +
        	'<div data-dojo-type="dijit.form.TextBox" type="password" id="pass"></div><br/>' +
            '<button data-dojo-type="dijit.form.Button" data-dojo-props="onClick:doAuthenticate" type="submit">Login</button>'
    });

    button = new DropDownButton({
        label: "Login",
        dropDown: dialog
    });

    usernameSpan = domConstruct.create("span", { innerHTML: '<strong>User: </strong><span id="authenticatedUser"></span>',
                                                     style: { visibility: "hidden" }});


    var loginDiv = dom.byId("login");
    loginDiv.appendChild(button.domNode);
    loginDiv.appendChild(usernameSpan);




    updateAuthentication();
});