<%@page contentType="text/html; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page import="edu.stanford.muse.LabelManager.Label" %>
<%@page import="edu.stanford.muse.LabelManager.LabelManager" %>
<%@page import="java.util.GregorianCalendar" %>
<%@page import="java.util.Date" %>
<%@page import="java.util.Calendar" %>
<%@include file="getArchive.jspf" %>
<!DOCTYPE html>


<html>

<%-- Jquery was present here last time--%>
<head>
	<link rel="icon" type="image/png" href="images/epadd-favicon.png"/>

	<link rel="stylesheet" href="bootstrap/dist/css/bootstrap.min.css"/>
	<jsp:include page="css/css.jsp"/>
    <link rel="stylesheet" href="css/main.css">
    <link rel="stylesheet" href="js/jquery-ui/jquery-ui.css">
    <link rel="stylesheet" href="js/jquery-ui/jquery-ui.theme.css">

	<%-- Jquery was present here earlier --%>
    <script src="js/jquery.js"></script>
    <script type="text/javascript" src="bootstrap/dist/js/bootstrap.min.js"></script>
    <script src="js/jquery-ui/jquery-ui.js"></script>

    <script src="js/selectpicker.js"></script>
	<script src="js/modernizr.min.js"></script>
	<script src="js/sidebar.js"></script>
	<script type="text/javascript" src="js/muse.js"></script>
	<script src="js/epadd.js"></script>
</head>
<body>
<%-- Header.jspf was present here earlier --%>
<%@include file="header.jspf"%>
<title><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages", "edit-label.head-edit-label")%></title>

<%
    // labelId = null or empty => tis is a new label

    // which lexicon? first check if url param is present, then check if url param is specified
    String labelID = request.getParameter("labelID");
    String labelName = "", labelDescription = "", labelType = "";
    String restrictionType = LabelManager.RestrictionType.OTHER.toString();
    String restrictionUntilTime = "";
    String restrictedForYears = "";
    String labelAppliesToMessageText= "";

    Label label = null;
    if (!Util.nullOrEmpty(labelID)) {
        label = archive.getLabelManager().getLabel(labelID);
        if (label != null) {
            labelName = label.getLabelName();
            labelDescription = label.getDescription();
            labelType = label.getType().toString();
            restrictionType = (label.getRestrictionType() != null) ? label.getRestrictionType().toString() : "";
            if (label.getRestrictedUntilTime() > 0) {
                // convert from long to yyyy-mm-dd
                Calendar c = new GregorianCalendar();
                c.setTime (new Date(label.getRestrictedUntilTime()));
                // don't forget the +1 adjustment for month
                restrictionUntilTime = String.format ("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DATE));
            }
            if (label.getRestrictedForYears() > 0)
                restrictedForYears = Long.toString(label.getRestrictedForYears());

            labelAppliesToMessageText = label.getLabelAppliesToMessageText();
            if (labelAppliesToMessageText == null)
                labelAppliesToMessageText  = "";
        }
    }
%>
<% writeProfileBlock(out, archive, (Util.nullOrEmpty(labelID) ? edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.create-label") : edu.stanford.muse.util.Messages.getMessage(archiveID,"messages", "edit-label.edit-label") + labelName)); %>
<br/>
<br/>
<br/>

<!-- when posted, this form goes back to labels screen -->
<form id="save-label-form">
    <div class="container">
    <!--row-->
    <div class="one-line">
        <!--form-wraper-->
        <div class="form-wraper clearfix panel">
            <input name="labelID" type="hidden" value="<%=(labelID == null) ? "": labelID  %>" class="form-control"/>
            <input name="archiveID" type="hidden" value="<%=archiveID%>" class="form-control"/>

        <div class="one-line">
            <h4><%=(Util.nullOrEmpty(labelID) ? edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.create-label") : edu.stanford.muse.util.Messages.getMessage(archiveID,"messages", "edit-label.edit-label") )%></h4>
            <br/>
            <br/>
            <!--File Name-->
            <div class="margin-btm col-sm-6">

                <!--input box-->
                <div class="form-group">
                    <label for="labelName"><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.label-name")%></label>
                    <input name="labelName" id="labelName" type="text" class="form-control" value="<%=labelName%>">
                </div>
            </div>

            <!--File Size-->
            <div class="form-group col-sm-6">
                <label for="labelType"><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.label-type")%></label>
                <select id="labelType" name="labelType" class="form-control selectpicker">
                    <option value="" selected disabled><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label-choose-label-type")%></option>
                    <% if (ModeConfig.isProcessingMode()) { %>
                        <option value="<%=LabelManager.LabType.PERMISSION.toString()%>" <%=LabelManager.LabType.PERMISSION.toString().equals(labelType) ? "selected":""%> ><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.choose-label-type.permission")%></option>
                    <% } %>
                    <%if(!ModeConfig.isDiscoveryMode() && !ModeConfig.isDeliveryMode()){%>
                        <option value="<%=LabelManager.LabType.RESTRICTION.toString()%>" <%=LabelManager.LabType.RESTRICTION.toString().equals(labelType) ? "selected":""%> ><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.choose-label-type.restriction")%></option>
                    <%}%>
                    <option value="<%=LabelManager.LabType.GENERAL.toString()%>"  <%=LabelManager.LabType.GENERAL.toString().equals(labelType) ? "selected":""%> ><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.choose-label-type.general")%></option>
                </select>
            </div>
        </div>

        <div class="one-line">
            <!--Type-->
            <div class="form-group col-sm-12">

                <!--input box-->
                <div class="form-group">
                    <label for="labelDescription"><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.label-description")%></label>
                    <input name="labelDescription" id="labelDescription" type="text" class="form-control" value="<%=Util.escapeHTML(labelDescription)%>">
                </div>

            </div>

        </div>

        <!-- below div is shown only if it's a restriction label -->
        <div style="" class="restriction-details one-line">
            <br/>
            <div class="form-group col-sm-6">
                <label for="restrictionType"><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.restriction-type")%></label>
                <select id="restrictionType" name="restrictionType" class="form-control selectpicker">
                    <option value="" selected disabled><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.restriction-type")%></option>
                    <option value="<%=LabelManager.RestrictionType.OTHER.toString()%>"
                            <%=LabelManager.RestrictionType.OTHER.toString().equals(restrictionType) ? "selected":""%>
                    ><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.restriction-type.not-actionable")%></option>
                    <option value="<%=LabelManager.RestrictionType.RESTRICTED_UNTIL.toString()%>"
                                 <%=LabelManager.RestrictionType.RESTRICTED_UNTIL.toString().equals(restrictionType) ? "selected":""%>
                            ><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.restriction-type.until-date")%></option>
                    <option value="<%=LabelManager.RestrictionType.RESTRICTED_FOR_YEARS.toString()%>"
                        <%=LabelManager.RestrictionType.RESTRICTED_FOR_YEARS.toString().equals(restrictionType) ? "selected":""%>
                        ><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.restriction-type.years-from-date-of-mess")%></option>
                </select>
            </div>

            <div class="form-group">
                <div style="display:none" class="div-restrictedUntil form-group col-sm-6">
                    <label for="restrictedUntil"><i class="fa fa-calendar"></i> Restricted until (yyyy-mm-dd)</label>
                    <input name="restrictedUntil" id="restrictedUntil" type="text" class="form-control" value="<%=restrictionUntilTime%>" readonly="true" style="cursor: pointer;background:white;">
                </div>
                <div style="display:none" class="div-restrictedForYears form-group col-sm-6">
                    <label for="restrictedForYears">Restricted for (years)</label>
                    <input name="restrictedForYears" id="restrictedForYears" type="text" class="form-control" value="<%=restrictedForYears%>">
                </div>
                <%--<div class="div-labelAppliesToMessageText form-group col-sm-12">
                    <label for="labelAppliesToMessageText">Restricted text</label>
                    <input name="labelAppliesToMessageText" id="labelAppliesToMessageText" type="text" class="form-control" value="<%=labelAppliesToMessageText%>">
                </div>--%>
            </div>

        </div>

    </div>
    <div style="text-align:center">
        <button class="btn btn-cta" type="submit" id="save-button"><%=edu.stanford.muse.util.Messages.getMessage(archiveID,"messages","edit-label.update-button")%> <i class="icon-arrowbutton"></i> </button>
    </div>
    </div>
</div>
</form>
<br/>
<script>
    $(document).ready(function() {
        // show or hide restrictions-details div based on whether it's a general or restriction label
        function label_type_refresh () {
            var type = $('#labelType').selectpicker('val');
            if ('<%=LabelManager.LabType.RESTRICTION.toString()%>' === type)
                $('.restriction-details').show();
            else
                $('.restriction-details').hide();
        }

        // show/hide the correct option based on restriction type
        function restriction_type_refresh() {
            var type = $('#restrictionType').selectpicker('val');
            if ('<%=LabelManager.RestrictionType.RESTRICTED_FOR_YEARS.toString()%>' === type) {
                $('.div-restrictedForYears').show();
                $('.div-restrictedUntil').hide();
            } else if ('<%=LabelManager.RestrictionType.RESTRICTED_UNTIL.toString()%>' === type) {
                $('.div-restrictedForYears').hide();
                $('.div-restrictedUntil').show();
            } else if ('<%=LabelManager.RestrictionType.OTHER.toString()%>' === type) {
                $('.div-restrictedForYears').hide();
                $('.div-restrictedUntil').hide();
            }
        }

        function do_save(event) {
            // get the form data using jquery's method
            var formData = $('form').serialize();

            // process the form
            $.ajax({
                type        : 'POST', // define the type of HTTP verb we want to use (POST for our form)
                url         : 'ajax/createEditLabels.jsp', // the url where we want to POST
                data        : formData, // our data object
                dataType    : 'json', // what type of data do we expect back from the server
                success: function(response) {
                    if (!response || response.status !== 0) {
                        epadd.error("Error saving label. " + ((response && response.errorMessage) ? response.errorMessage : " (unknown reason"));
                    } else {
                        epadd.success('Label saved.', function () {window.location = 'labels?archiveID=<%=archiveID%>'});
                    }
                },
                error: function(jq, textStatus, errorThrown) {
                    epadd.error("Error saving label. (Details: status = \" + textStatus + ' json = ' + jq.responseText + ' errorThrown = ' + errorThrown + \"\\n\" + printStackTrace() + \")\"");
                }
            });

            // stop the form from submitting the normal way and refreshing the page
            event.preventDefault();
        }

        $('#labelType').on ('change', label_type_refresh);
        $('#restrictionType').on ('change', restriction_type_refresh);

        // also call once to ensure the correct options are selected
        label_type_refresh();
        restriction_type_refresh();

        // process the form
        <%--Code base/template to submit this form data using ajax. The steps are as following;--%>
        <%--1.Capture the form submit button so that the default action doesn't take place--%>
        <%--2.Get all of the data from our form using jQuery--%>
        <%--3.Submit using AJAX --%>
        <%--4.Show errors if there are any--%>
        <%--//https://scotch.io/tutorials/submitting-ajax-forms-with-jquery--%>


        $('#restrictedUntil').datepicker({
            minDate: new Date(1960, 1 - 1, 1),
            dateFormat: "yy-mm-dd",
            changeMonth: true,
            changeYear: true,
            yearRange: "2000:2100"
        });

        $('#save-label-form').submit(do_save);
    });
</script>

<jsp:include page="footer.jsp"/>
</body>
</html>
