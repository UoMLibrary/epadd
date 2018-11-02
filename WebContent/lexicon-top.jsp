<%@ page import="edu.stanford.muse.Config" %>
<%@page language="java" import="java.util.Collection"%>
<%@page language="java" import="java.util.LinkedHashSet"%>
<%@ page import="java.util.Map" %>
<%@ page import="edu.stanford.muse.index.*" %>
<%@ page import="org.json.JSONArray" %>
<%@include file="getArchive.jspf" %>
<!DOCTYPE HTML>
<html>
<head>
	<title>Lexicon</title>
	<link rel="icon" type="image/png" href="images/epadd-favicon.png">
	<link href="css/jquery.dataTables.css" rel="stylesheet" type="text/css"/>
	<link rel="stylesheet" href="bootstrap/dist/css/bootstrap.min.css">
	<jsp:include page="css/css.jsp"/>
	<link rel="stylesheet" href="css/sidebar.css">
	<link rel="stylesheet" href="css/main.css">

	<script src="js/jquery.js"></script>
	<script src="js/jquery.dataTables.min.js"></script>
	<script type="text/javascript" src="bootstrap/dist/js/bootstrap.min.js"></script>

	<script src="js/modernizr.min.js"></script>
	<script src="js/sidebar.js"></script>
	<script src="js/muse.js"></script>

	<script src="js/epadd.js"></script>
	
	<style type="text/css">
     /*.js #table  {display: none;}*/
      .search {cursor:pointer;}
    </style>
	<style>
		.modal-body {
			/* 100% = dialog height, 120px = header + footer */
			max-height: calc(100% - 120px);
			overflow-y: scroll;
		}
	</style>

	<%
		boolean isDelivery = ModeConfig.isDeliveryMode()?true:false;
		JSONArray lexiconsWithCategories = archive.getAvailableLexiconsWithCategories(isDelivery);
		JSONArray lexiconnames = new JSONArray();
		for(int i=0;i<lexiconsWithCategories.length();i++)
		    lexiconnames.put(i,lexiconsWithCategories.getJSONArray(i).get(0));

	%>

</head>
<body>
<%@include file="header.jspf"%>
<script>epadd.nav_mark_active('Browse');</script>

<%writeProfileBlock(out, false, archive, "Lexicons", 900);%>

<!--sidebar content-->
<div class="nav-toggle1 sidebar-icon">
	<img src="images/sidebar.png" alt="sidebar">
</div>

<nav class="menu1" role="navigation">
	<h2>Lexicon Tips</h2>
	<!--close button-->
	<a class="nav-toggle1 show-nav1" href="#">
		<img src="images/close.png" class="close" alt="close">
	</a>

	<p>Lexicons are customizable saved searches containing categories of keywords.
	<p>Define the default lexicon using the config.properties file.
	<p>Selecting a lexicon category from this screen will display the messages containing keywords in that category.
	<p>Select a different lexicon using the Choose Lexicon dropdown.
	<p>Lexicons can be viewed in detail and edited by selecting the View/Edit Lexicon button.
	<p>Create a new lexicon by selecting the Create New Lexicon button.

</nav>
<!--/sidebar-->


<%

		/*if (ModeConfig.isDeliveryMode()) {
			lexiconNames = new LinkedHashSet(lexiconNames); // we can't call remve on the collection directly, it throws an unsupported op.
			lexiconNames.remove(Lexicon.SENSITIVE_LEXICON_NAME);
		}
*/

%>
			

<div style="margin:auto; width:900px">
	<div class="button_bar_on_datatable">
		<%if(!ModeConfig.isDiscoveryMode()){%>
		<div title="Create lexicon" class="buttons_on_datatable" id="create-lexicon"><img class="button_image_on_datatable" src="images/add_lexicon.svg"></div>
		<div title="Upload lexicon" class="buttons_on_datatable" id="import-lexicon" ><img class="button_image_on_datatable" src="images/upload.svg"></div>
		<%}%>
	</div>
	<table id="table">
		<thead><th>Lexicon</th><th>Number of Categories</th></thead>
		<tbody>			
			<%
			for (int j=0; j<lexiconsWithCategories.length();j++) {
				%>
				<tr><td class="search"><%=((JSONArray)lexiconsWithCategories.get(j)).get(0) %></td><td><%=((JSONArray)lexiconsWithCategories.get(j)).get(1)%></td></tr>
				<%
			}
			%>
		</tbody>
	</table>
</div>
<p>
<br/>

<div>
	<div id="lexicon-upload-modal" class="info-modal modal fade" style="z-index:99999">
		<div class="modal-dialog">
			<div class="modal-content">
				<div class="modal-header">
					<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
					<h4 class="modal-title">Upload a lexicon file</h4>
				</div>
				<div class="modal-body">
					<form id="uploadlexiconform" method="POST" enctype="multipart/form-data" >
						<input type="hidden" value="<%=archiveID%>" name="archiveID"/>
						<div class="form-group **text-left**">
							<label for="lexicon-name" class="col-sm-2 control-label **text-left**">Name</label>
							<div class="col-sm-10">
								<input id="lexicon-name" class="dir form-control" type="text"  value="" name="lexicon-name"/>
							</div>
						</div>
						&nbsp;&nbsp;
						<!--
						<div class="form-group **text-left**">
							<!-- <label for="lexicon-lang" class="col-sm-2 control-label **text-left**">Language</label>
							<div class="col-sm-10">
								<input type="hidden" id="lexicon-lang" class="dir form-control" value="english" name="lexicon-lang"/>
							</div>
						</div>
						&nbsp;&nbsp;
						-->
					<input type="hidden" id="lexicon-lang" class="dir form-control" value="english" name="lexicon-lang"/>

					<div class="form-group">
							<label for="lexiconfile" class="col-sm-2 control-label **text-left**">File</label>
							<div class="col-sm-10">
								<input type="file" id="lexiconfile" name="lexiconfile" value=""/>
							</div>
						</div>
						<%--<input type="file" name="correspondentCSV" id="correspondentCSV" /> <br/><br/>--%>

					</form>
				</div>
				<div class="modal-footer">
					<button id="upload-lexicon-btn" class="btn btn-cta" onclick="uploadLexiconHandler();return false;">Upload <i class="icon-arrowbutton"></i></button>


					<%--<button id='overwrite-button' type="button" class="btn btn-default" data-dismiss="modal">Overwrite</button>--%>
					<%--<button id='cancel-button' type="button" class="btn btn-default" data-dismiss="modal">Cancel</button>--%>
				</div>
			</div><!-- /.modal-content -->
		</div><!-- /.modal-dialog -->
	</div><!-- /.modal -->
</div>

<script type="text/javascript" charset="utf-8">
    // we're using a simple datatables data source here, because this will be a small table
    $('html').addClass('js'); // see http://www.learningjquery.com/2008/10/1-way-to-avoid-the-flash-of-unstyled-content/
    $(document).ready(function() {
        /*function do_lexicon_search(e) {
          var cat = $(e.target).text();
          if (window.is_regex)
              window.open('browse?adv-search=1&archiveID=&sensitive=true&termBody=on&termSubject=on&termAttachments=on&lexiconCategory=' + cat + '&lexiconName=' + $('#lexiconName').val()); // sensitive=true is what enables regex highlighting
          else
              window.open('browse?adv-search=1&archiveID=&lexiconCategory=' + cat + '&termAttachments=on&termBody=on&termSubject=on&lexiconName=' + $('#lexiconName').val());
        }
        //if the paging is set, then the lexicon anchors in the subsequent pages are not hyperlinked. Lexicons typically do not need paging, so we list all categories in one page
        var oTable = $('#table').dataTable({paging:false, columnDefs: [{ className: "dt-right", "targets": 1}]});
        oTable.fnSort( [ [1,'desc'] ] );
        $('#table').show();
*/
        function changeLexicon(e) {
            var lexname = $(e.target).text();
            window.location = 'lexicon?archiveID=<%=archiveID%>&lexicon=' +	lexname;
        }
        var oTable = $('#table').dataTable({paging:false, columnDefs: [{ className: "dt-right", "targets": 1}]});
        oTable.fnSort( [ [1,'desc'] ] );
        $('#table').show();
        // attach the click handlers
        $('.search').click(changeLexicon);


        $('#create-lexicon').click (function() {
            var lexiconName = prompt ('Enter the name of the new lexicon:');
            if (!lexiconName)
                return;
            window.location = 'edit-lexicon?archiveID=<%=archiveID%>&lexicon=' + lexiconName;
        });

        $('#import-lexicon').click(function(){
            //open modal box to get the lexicon file and upload
            $('#lexicon-upload-modal').modal('show');
        });

    } );
    var uploadLexiconHandler=function() {
        //collect archiveID,lexicon-name and lexiconfile field. If either of them is empty return false;
        var lexiconname = $('#lexicon-name').val();
        var existinlexiconnames = <%=lexiconnames.toString(5)%>;
        if (!lexiconname) {
            alert('Please provide the name of the lexicon');
            return false;
        }
        var lexiconlang = $('#lexicon-lang').val();
        if (!lexiconlang) {
            alert('Please provide the language of the lexicon');
            return false;
        }
        var lexiconfilename = $('#lexiconfile').val();
        if (!lexiconfilename) {
            alert('Please provide the path of the lexicon file');
            return false;
        }
        var actual_upload = function () {
            var form = $('#uploadlexiconform')[0];

            // Create an FormData object
            var data = new FormData(form);
            //hide the modal.
            $('#lexicon-upload-modal').modal('hide');
            //now send to the backend.. on it's success reload the labels page. On failure display the error message.

            $.ajax({
                type: 'POST',
                enctype: 'multipart/form-data',
                processData: false,
                url: "ajax/upload-lexicon.jsp",
                contentType: false,
                cache: false,
                data: data,
                success: function (data) {
                    epadd.success('Lexicon uploaded successfully.', function () {window.location.reload();});
                },
                error: function (jq, textStatus, errorThrown) {
                    var message = ("Error uploading file, status = " + textStatus + ' json = ' + jq.responseText + ' errorThrown = ' + errorThrown);
                    epadd.error(message);
                }
            });
        }
        //if lexicon-name is already one of the lexicon then prompt a confirmation box
        if (existinlexiconnames.indexOf(lexiconname.toLowerCase()) > -1) {

            epadd.warn_confirm_continue('A lexicon with the same name already exists. This import will overwrite the existing lexicon. Do you want to continue?', actual_upload);
        }else
            actual_upload();
    }

</script>
<jsp:include page="footer.jsp"/>
</body>
</html>
