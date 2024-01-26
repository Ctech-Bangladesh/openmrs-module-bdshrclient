<html>
    <head>
        <title>Master Client Index Search Page</title>
        <link rel="shortcut icon" type="image/ico" href="/${ ui.contextPath() }/images/openmrs-favicon.ico"/>
        <link rel="icon" type="image/png\" href="/${ ui.contextPath() }/images/openmrs-favicon.png"/>

        <%
            ui.includeJavascript("uicommons", "jquery-1.8.3.min.js", Integer.MAX_VALUE)
            ui.includeCss("uicommons", "styleguide/jquery-ui-1.9.2.custom.min.css", Integer.MAX_VALUE - 10)
            ui.includeJavascript("uicommons", "jquery.toastmessage.js", Integer.MAX_VALUE - 20)
            ui.includeJavascript("shrclient", "mustache.js", Integer.MAX_VALUE - 30)
            ui.includeJavascript("shrclient", "validation.js", Integer.MAX_VALUE - 30)
            ui.includeCss("uicommons", "styleguide/jquery.toastmessage.css", Integer.MAX_VALUE - 20)
        %>

        <style>
            body {
                font-family: Arial, Sans-serif;
                font-size: 12px;
                color: #333;
            }
            .container {
                width: 960px;
                margin: 0 auto;
            }
            h1 {
                font-size: 24px;
                color: #438D80;
            }
            .btn {
                padding: 10px 20px;
                background: #88af28;
                color: #fff;
                border: 1px solid #88af28;
                font-size: 14px;
                border-radius: 3px;
            }
            .download-btn {
                float: right;
            }
            .patient-id {
                border: 2px solid #ccc;
                padding: 2px 10px;
                height: 30px;
                width: 230px;
            }
            .id-type {
                border: 1px solid #ccc;
                height: 30px;
                width: 130px;
            }
            .search-box {
                margin-top: 20px;
                margin-bottom: 10px;
            }
            .search-results ul li{
                padding: 10px;
                background: #eee;
                border-radius: 5px;
                margin-bottom: 1px;
                overflow: auto;
                clear: both;
            }
            .result-details {
                float: left;
            }
            .patient-name {
                font-size: 14px;
                margin-bottom: 5px;
            }
            .health-id {
                font-size: 12px;
                color: #666;
            }
            .merge-with {
                color:#FF3D3D
            }
            .father-info, .address {
                font-size: 12px;
                color: #666;
            }
            .errorMessage {
                padding:2px 4px;
                margin:0px;
                border:solid 1px #FBD3C6;
                background:#FDE4E1;
                color:#CB4721;
                font-family:Arial, Helvetica, sans-serif;
                font-size:14px;
                font-weight:bold;
            }
            .merge-detail {
                padding: 10px;
                background: #eee;
                border-radius: 5px;
                margin-bottom: 1px;
                overflow: auto;
                clear: both;
            }
            .mergedIdentifierList {
                list-style-type: disc;
                margin-left: 10px;
                padding: 0;
            }
            .mciPatientInfo {
                margin: 10px;
                padding: 0;
                list-style-type: none;
            }
            .continue-btn {
                margin: 5px;
            }
        </style>

        ${ ui.resourceLinks() }
    </head>
    <body>
        <script type="text/javascript">
            var OPENMRS_CONTEXT_PATH = '${ ui.contextPath() }';
            window.translations = window.translations || {};
        </script>

        <ul id="breadcrumbs"></ul>

        <div id="body-wrapper">
            ${ ui.includeFragment("uicommons", "infoAndErrorMessage") }
            <div id="content" class="container">
                <h1>Search Patient in National Registry</h1>
                <div style="display:none" class="errorMessage"></div>
                <div id="searchBox" class="search-box">
                    <select id="idType" class="id-type">
                      <option value="nid">National ID</option>
                      <option value="hid">Health ID</option>
                      <option value="brn">Birth Registration Number</option>
                      <option value="uid">Unique Identifier</option>
                      <option value="houseHoldCode">Household ID</option>
                      <option value="phoneNo">Phone Number</option>
                    </select>
                    <input id="patientId" class="patient-id" type="text" name="patientId" value="">
                </div>
                <div id="searchResults" class="search-results">
                </div>

                <div id="mergeDetails" style="display:none" class="merge-detail"></div>
            </div>
        </div>

        <script id="mciPatientTmpl" type="x-tmpl-mustache">
            <ul class="mciPatientInfo">
                {{#.}}
                    <li class="mciPatientInfo">
                        {{#active}}
                            <div id="resultDetails" class="result-details">
                                <div><span class="patient-name">{{ firstName }} {{ middleName }} {{ lastName }} </span> <span class="gender">({{ gender }})</span></div>
                                <div><span class="health-id">Health Id : {{ healthId }}</span></div>
                                <div><span class="address">Address: {{address.addressLine}}, {{ address.union }}, {{ address.upazilla }}, {{ address.district }}, {{ address.division }}</span></div>
                            </div>
                            <button data-hid="{{ healthId }}" class="download-btn btn">Download</button>
                        {{/active}}
                        {{^active}}
                            <div class="inactive-patient-block">
                                <div><span>The patient with Health Id(<b>{{ inactiveHID }}</b>) is no longer active. It was merged with the patient with Health Id(<b>{{ healthId }}</b>)</span></div>
                                <div><span>Please download <b>{{ healthId }}</b> instead.</span></div>
                                <div id="resultDetails" class="result-details">
                                    <div><span class="patient-name">{{ firstName }} {{ middleName }} {{ lastName }} </span> <span class="gender">({{ gender }})</span></div>
                                    <div><span class="health-id">Health Id : {{ healthId }}</span></div>
                                    <div><span class="address">Address: {{address.addressLine}}, {{ address.union }}, {{ address.upazilla }}, {{ address.district }}, {{ address.division }}</span></div>
                                </div>
                                <button data-hid="{{ healthId }}" inactive-hids="{{ inactiveHIDs }}" class="download-btn btn">Download</button>
                            <div>
                        {{/active}}
                    </li>
                {{/.}}
            </ul>
        </script>

        <script id="mergePatientDetail" type="x-tmpl-mustache">
            <div><span>The valid Health Id is <b>{{ hid }} ({{#localIds}}{{ . }}{{#comma}}, {{/comma}}{{/localIds}})</b></span></div>
            <div><span>Invalid Health Ids are : </span></div>
            <ul class="mergedIdentifierList">
                {{#mergedIdentifiers}}
                <li class="mergedIdentifierList">
                <span>{{ hid }} ({{#localIds}}{{ . }}{{#comma}}, {{/comma}}{{/localIds}})</span>
                </li>
                {{/mergedIdentifiers}}
            </ul>
            <button uuid="{{ uuid }}" class="continue-btn btn">Continue</button>
        </script>

        <script type="text/javascript">
            jq = jQuery;
            jq(function(){
                jq('#patientId').keypress(function(e) {
                   if(e.which == 13) {
                       searchPatient();
                    }
                });
            });
            
            function onError(error){
                var message = "Error occurred. Could not perform the action.";
                if(error.status === 401){
                    message = "Privileges to download patient is not present.";
                }
                jq(".errorMessage").text(message);
                jq(".errorMessage").show();
            }

            function searchPatient() {
                jq(".errorMessage").hide();
                jq('#searchResults').hide();
                var idType = jq( "#idType" ).val().trim();
                var patientId = jq( "#patientId" ).val().trim();
                if(!isPatientIdValid(idType,patientId)) return;
                jq.ajax({
                   type: "GET",
                   url: "/openmrs/ws/mci/search?" + idType + "=" + patientId,
                   dataType: "json"
                }).done(function( responseData ) {
                   renderMciPatient(responseData);
                }).fail(onError);
            }

            function downloadMciPatient(e) {
                jq(".errorMessage").hide();
                var url = "/openmrs/ws/mci/download?hid=" + jq(e.target).attr("data-hid");
                if(jq(e.target).attr("inactive-hids")) {
                    url = url.concat("&inactiveHids=" + jq(e.target).attr("inactive-hids"));
                }
                jq.ajax({
                   type: "GET",
                   url: url,
                   dataType: "json"
                }).done(function( responseData ) {
                    if (!responseData) {
                       jq(".errorMessage").text("Error occurred. Could not perform the action.");
                       jq(".errorMessage").show();
                    }
                    else {
                        if (responseData.mergedIdentifiers) {
                            renderInvalidHidPage(responseData)
                        } else {
                            redirectToRegistrationPage(responseData.uuid);
                        }
                    }
                }).fail(onError);
            }

            function renderMciPatient(patients) {
                jq(".download-btn").unbind("click", downloadMciPatient);
                if (!patients) {
                    jq("#searchResults").html("No patient was found in National Registry");
                    jq('#searchResults').show();
                    return;
                }
                var template = jq('#mciPatientTmpl').html();
                Mustache.parse(template);
                var rendered = Mustache.render(template, patients);
                jq('#searchResults').html(rendered);
                jq('#searchResults').show();
                jq(".download-btn").bind("click", downloadMciPatient);
            }

            function renderInvalidHidPage(responseData) {
                responseData.mergedIdentifiers.forEach(function (mergedIdentifier) {
                    mergedIdentifier.localIds = convertListToStringList(mergedIdentifier.localIds);
                });
                responseData.localIds = convertListToStringList(responseData.localIds);
                jq(".continue-btn").unbind("click", redirectOnContinue);
                jq('#searchResults').hide();
                jq('#searchBox').hide();
                var template = jq('#mergePatientDetail').html();
                Mustache.parse(template);
                var rendered = Mustache.render(template, responseData);
                jq('#mergeDetails').html(rendered);
                jq("#mergeDetails").show();
                jq(".continue-btn").bind("click", redirectOnContinue);
            }

            function convertListToStringList(list) {
                var listString = list.join(", ");
                return listString;
            }

            function redirectOnContinue(e) {
                redirectToRegistrationPage(jq(e.target).attr("uuid"));
            }

            function redirectToRegistrationPage(uuid) {
                window.location = "/bahmni/registration/#/patient/" + uuid;
            }
        </script>
    </body>
</html>



