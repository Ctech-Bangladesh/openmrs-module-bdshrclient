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
            ui.includeCss("uicommons", "styleguide/jquery.toastmessage.css", Integer.MAX_VALUE - 20)
        %>

        <style>
            body {
                font-family: Arial, Sans-serif;
                font-size: 12px;
                color: #333;
            }
            ul {margin: 0; padding: 0;}
            ul li {list-style-type: none;}

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
                <div style="display:none" class="errorMessage">Error occurred. Could not perform the action.</div>
                <div id="searchBox" class="search-box">
                    <select id="idType" class="id-type">
                      <option value="nid">National ID</option>
                      <option value="hid">Health ID</option>
                    </select>
                    <input id="patientId" class="patient-id" type="text" name="patientId" value="">
                </div>
                <div id="searchResults" class="search-results">
                </div>
            </div>
        </div>

        <script id="mciPatientTmpl" type="x-tmpl-mustache">
            <ul class="mciPatientInfo">
                {{#.}}
                <li>
                    <div id="resultDetails" class="result-details">
                        <div><span class="patient-name">{{ firstName }} {{ middleName }} {{ lastName }} </span> <span class="gender">({{ gender }})</span></div>
                        <div class="father-info">Primary Contact: {{ primaryContact }} </div>
                        <span class="address">Address: {{address.addressLine}}, {{ address.union }}, {{ address.upazilla }}, {{ address.district }}, {{ address.division }}</span>
                    </div>

                    <button data-hid="{{ healthId }}" class="download-btn btn">Download</button>
                </li>
                {{/.}}
            </ul>
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

            function searchPatient() {
                jq(".errorMessage").hide();
                var idType = jq( "#idType" ).val();
                var patientId = jq( "#patientId" ).val();
                jq.ajax({
                   type: "GET",
                   url: "/openmrs/ws/mci/search?" + idType + "=" + patientId,
                   dataType: "json"
                }).done(function( responseData ) {
                   renderMciPatient(responseData);
                }).fail(function(error) {
                   jq(".errorMessage").show();
                });
            }

            function downloadMciPatient(e) {
                jq(".errorMessage").hide();
                jq.ajax({
                   type: "GET",
                   url: "/openmrs/ws/mci/download?hid=" + jq(e.target).attr("data-hid"),
                   dataType: "json"
                }).done(function( responseData ) {
                    if (!responseData) {
                       jq(".errorMessage").show();
                    }
                    else {
                       window.location = "/bahmni/registration/#/patient/" + responseData.uuid;
                    }
                }).fail(function(error) {
                   jq(".errorMessage").show();
                });
            }

            function renderMciPatient(patients) {
                jq(".download-btn").unbind("click", downloadMciPatient);
                if (!patients) {
                    jq("#searchResults").html("No patient was found in National Registry");
                    return;
                }
                var template = jq('#mciPatientTmpl').html();
                Mustache.parse(template);
                var rendered = Mustache.render(template, patients);
                jq('#searchResults').html(rendered);
                jq(".download-btn").bind("click", downloadMciPatient);
            }
        </script>

    </body>
</html>



