<%@page contentType="text/html; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="edu.stanford.muse.AddressBookManager.AddressBook"%>
<%@page language="java" import="edu.stanford.muse.index.Archive"%>
<%@page language="java" import="java.util.Set"%>
<%@ page import="edu.stanford.muse.webapp.ModeConfig" %>
<%@ page import="edu.stanford.muse.email.GmailAuth.AuthenticatedUserInfo" %>
<%@page language="java" %>
<!DOCTYPE HTML>
<html>
<head>
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>Specify Email Sources</title>
	<link rel="icon" type="image/png" href="images/epadd-favicon.png">


	<link rel="stylesheet" href="bootstrap/dist/css/bootstrap.min.css">
	<link href="jqueryFileTree/jqueryFileTree.css" rel="stylesheet" type="text/css" media="screen" />
	<jsp:include page="css/css.jsp"/>
	<link rel="stylesheet" href="css/sidebar.css">
    <link rel="stylesheet" href="css/main.css">

    <style>
		.div-input-field { display: inline-block; width: 400px; margin-left: 20px; line-height:10px; padding:20px; vertical-align: top;}
		.input-field {width:350px;}
		.input-field-label {font-size: 12px;}

		#customBtn:hover {
			cursor: pointer;
		}

		.provider-icon {
			position: absolute;
			left: 20px;
			top: 13px;
		}

		.login-container .provider-link {
			color: white;
			display: block;
			font-size: 16px;
			height: 40px;
			line-height: 40px;
			position: relative;
			text-align: center;
			width: 100%;
			margin-bottom: 20px;
		}

		#login-google {
			background-color: #bf4434;
		}


	</style>

	<script src="js/jquery.js"></script>
	<script src="https://apis.google.com/js/api:client.js"></script>
	<script src="https://apis.google.com/js/client:platform.js" async defer></script>
	<script type="text/javascript" src="bootstrap/dist/js/bootstrap.min.js"></script>
	<script src="jqueryFileTree/jqueryFileTree.js"></script>
	<script src="js/filepicker.js"></script>
	<script src="js/modernizr.min.js"></script>
	<script src="js/sidebar.js"></script>

	<script src="js/muse.js"></script>
	<script src="js/epadd.js"></script>

</head>
<body style="background-color:white;">
<%@include file="header.jspf"%>
<jsp:include page="div_filepicker.jspf"/>

<% if (ModeConfig.isAppraisalMode()) { %>
	<script>epadd.nav_mark_active('Import');</script>
<% } else { %>
	<script>epadd.nav_mark_active('New');</script>
<% } %>

<div class="nav-toggle1 sidebar-icon">
    <img src="images/sidebar.png" alt="sidebar">
</div>

<!--sidebar content-->
<nav class="menu1" role="navigation">
    <h2>Import Help</h2>
    <!--close button-->
    <a class="nav-toggle1 show-nav1" href="#">
        <img src="images/close.png" class="close" alt="close">
    </a>

    <!--phrase-->
    <div class="search-tips">
		Add the name of archive owner and an associated email address; ePADD won’t work correctly without this information.
		<br/>
		<br/>

		Import mail via an IMAP connection or an .mbox file.
		<br/>
		<br/>

		Add the name of the email source when importing .mbox files to assist others in identifying the origin of associated messages.  This field is flexible and could include values like work, personal, office, laptop, etc. For email transferred via IMAP, the name of email source is assigned automatically as the email address associated with the archive owner.
		<br/>
		<br/>

		You can further refine your import on the next screen by specifying particular mail folders, as well as a range of dates.
		<br/>
		<br/>

		If you have email in non-mbox formats such as PST and Eudora, you can use programs like Emailchemy, Mailstore Home or Aid4Mail to convert them to mbox files.
    </div>
</nav>
<%
	//Term t;
	//initialization of JPL -- just for testing
	//JPL.init();
	//load model file..
Archive archive =  JSPHelper.getArchive(request);
String bestName = "";
String bestEmail = "";
if (archive != null) {
	AddressBook ab = archive.addressBook;
	Set<String> addrs = ab.getOwnAddrs();
	if (addrs.size() > 0)
		bestEmail = addrs.iterator().next();
	writeProfileBlock(out, archive, "Import email into this archive");
}
//Check if user is logged in (via gmail oauth) then fill in server username and password boxes already. So that the normal flow of adding account and login
	AuthenticatedUserInfo authInfo = (AuthenticatedUserInfo) session.getAttribute("authInfo");
	boolean loggedIn=true;
	if ((authInfo == null) || (authInfo.getAuthToken() == null)) { // authenticated session expired, log out of PM
		loggedIn = false;
	}
	String useremail = "";
	String xoauthtoken = "";
	String OAUTH_MAGIC_STRING="xoauth";
	if(loggedIn){
	    useremail = authInfo.getUserName();
	    xoauthtoken = OAUTH_MAGIC_STRING+authInfo.getAccessToken();
	}
%>
<%--
<!--sidebar content-->
<div class="nav-toggle1 sidebar-icon">
	<img src="images/sidebar.png" alt="sidebar">
</div>

<nav class="menu1" role="navigation">
	<h2>Import Tips</h2>
	<!--close button-->
	<a class="nav-toggle1 show-nav1" href="#">
		<img src="images/close.png" class="close" alt="close">
	</a>

	<!--phrase-->
	<div class="search-tips">
		<img src="images/pharse.png" alt="">
		<p>
			Text from Josh here.
		</p>
	</div>

</nav>
<!--/sidebar-->--%>

<p>

<form method="post" class="form-horizontal">
<div id="all_fields" style="margin-left:170px; width:900px; padding: 10px">
	<section>
		<div class="panel">
			<div class="panel-heading">About this archive</div>
			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-user"></i> Name of archive owner</div>
				<br/>
				<div class="input-field">
					<input title="Name of archive owner" class="form-control" type="text" name="name" value="<%=bestName%>"/>
				</div>
			</div>
			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-envelope"></i> Primary email address</div>
				<br/>
				<div class="input-field">
					<input class="form-control" type="text" name="alternateEmailAddrs" value="<%=bestEmail%>"/>
				</div>
			</div>

			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-tag"></i> Archive title</div>
				<br/>
				<div class="input-field">
					<input class="form-control" type="text" name="archiveTitle" value=""/>
				</div>
			</div>

		</div>
	</section>

	<section>
		<% /*For google oauth login */ %>
		<div id="serversimap" class="accounts panel">
			<div class="provider-link" style="width:100px;display:inline-block" id="login-google">
			<div id="customBtn">
				<span style="text-align:center;color:white;"><i class="fa fa-google fa-2"></i></span>
				<span class="provider-name">Google</span>
			</div>
			</div>
		</div>
	</section>

	<section>
	<div id="servers" class="accounts panel">
		<% /* proper field names and ids will be assigned later, when the form is actually submitted */ %>
		<div class="panel-heading">Public Email Accounts (Gmail, Yahoo, Hotmail, Live.com, etc)</div>

		<div class="account">
			<input class="accountType" type="text" style="display:none" id="accountType0" name="accountType0" value="email"/>

			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-envelope"></i> Email Address</div>
				<br/>
				<div class="input-field"><input class="form-control" type="text" name="loginName0" value="<%=useremail%>" /></div>
			</div>

			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-key"></i> Password <img class="spinner" id="spinner0" src="images/spinner3-black.gif" width="15" style="margin-left:10px;visibility:hidden"></div>
				<br/>
				<div class="input-field"><input class="form-control" type="password" name="password0" value="<%=xoauthtoken%>"/></div>
			</div>
			<br/>
		</div>
		<br/>

		<button style="margin-left:40px" class="btn-default" onclick="add_server(); return false;"><i class="fa fa-plus"></i> <%=edu.stanford.muse.util.Messages.getMessage("messages", "appraisal.email-sources.another-public-imap")%></button>
		<br/>
		<br/>
	</div> <!--  end servers -->
</section>

	<section>
	<div id="private_servers" class="accounts panel">
		<div class="panel-heading">
			Private Email IMAP Accounts (Google Apps, university account, corporate account, etc.)<br/>
		</div>
		<p></p>

		<% /* proper field names and ids will be assigned later, when the form is actually submitted */ %>
		<div class="account">
			<input class="accountType" type="text" style="display:none" id="accountType1" name="accountType1" value="email"/>

			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-server"></i> IMAP Server</div>
				<br/>
				<div class="input-field"><input class="form-control" type="text" name="server1"/></div>
			</div>
			<br/>

			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-envelope"></i> Email Address</div>
				<br/>
				<div class="input-field"><input class="form-control" type="text" name="loginName1"/></div>
			</div>

			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-key"></i> Password <img class="spinner" id="spinner1" src="images/spinner3-black.gif" width="15" style="margin-left:10px;visibility:hidden"><br/>
				</div>
				<br/>
				<div class="input-field"><input class="form-control" type="password" name="password1"/></div>
			</div>
			<br/>

		</div>
		<br/>
		<button style="margin-left:40px" class="btn-default" onclick="add_private_server(); return false;"><i class="fa fa-plus"></i> <%=edu.stanford.muse.util.Messages.getMessage("messages", "appraisal.email-sources.another-private-imap")%></button>
		<br/>
		<br/>

	</div> <!--  end servers -->
</section>


	<section>
		<div id="mboxes" class="accounts panel">
			<div class="panel-heading">
				Mbox files<br/>
			</div>

			<% /* all mboxes folders will be in account divs here */ %>
			<div class="account">
				<input class="accountType" type="text" style="display:none" name="accountType2" value="mbox"/>
				<div class="div-input-field">
					<div class="input-field-label"><i class="fa fa-folder-o"></i> Folder or file location</div>
					<br/>
					<div class="input-field" style="width:800px"> <!-- override default 350px width because we need a wider field and need browse button on side-->
                        <div class="form-group col-sm-8">
    						<input class="dir form-control" type="text" name="mboxDir2"/> <br/>
                        </div>
                        <div class="form-group col-sm-4">
                            <button style="height:37px" class="browse-button btn-default"><i class="fa fa-file"></i> <!-- special height for this button to make it align perfectly with input box. same height is used in export page as well -->
                                <span>Browse</span>
                            </button>
                        </div>
					</div>
					<br/>
				</div>
				<div class="div-input-field">
					<div class="input-field-label"><i class="fa fa-bullseye"></i> Name of email source</div>
					<br/>
					<div class="input-field">
						<input class="form-control" type="text" name="emailSource2" value=""/>
					</div>
				</div>

				<br/>
			</div> <!--  end account -->
			<br/>
			<button  style="margin-left:40px" class="btn-default" onclick="return add_mboxdir(); return false;"><i class="fa fa-plus"></i> Add folder</button>
			<br/>
			<br/>
		</div>
	</section>

	<div style="text-align:center;margin-top:20px">
		<button class="btn btn-cta" id="gobutton" onclick="epadd.do_logins(); return false">Next <i class="icon-arrowbutton"></i> </button>
	</div>
</div> <!--  all fields -->


</form>

<p>
<jsp:include page="footer.jsp"/>

	<script>
        var googleInitSignIn = function() {
            gapi.load('auth2', function(){
                // Retrieve the singleton for the GoogleAuth library and set up the client.
                auth2 = gapi.auth2.init({
                    client_id: '893886069594-hsbcr0elt6u0vgr2qa7qei7boop3jgu2.apps.googleusercontent.com',
                    // client_id: '606753072852-c2aoi9qu516lvo05gscdb017062a83hm.apps.googleusercontent.com',
                    cookiepolicy: 'single_host_origin',
                    // Request scopes in addition to 'profile' and 'email'
                    //scope: 'additional_scope'
                    // scope: 'email profile https://www.googleapis.com/auth/gmail.readonly'
                    scope: 'email profile https://www.googleapis.com/auth/gmail.readonly https://mail.google.com/'
                });
                attachSignin(document.getElementById('customBtn'));
            });
        };

        function attachSignin(element) {
            auth2.attachClickHandler(element, {},
                function(googleUser) {
                    //https://stackoverflow.com/questions/52883909/google-api-accessing-gmail-from-java-with-id-token-from-javascript
                    //authenticate(AUTH_METHOD.GOOGLE, googleUser.getAuthResponse().id_token); // The user has successfully singled in, pass the id token at the server
                    authenticate(googleUser.getAuthResponse().id_token, googleUser.getAuthResponse().access_token); // The user has successfully singled in, pass the id token at the server
                }, function(error) {
                    console.log("Google login failed");
                    // LOG("Google login failed:" + JSON.stringify(error, undefined, 2));
                });
        }
        function authenticate(idToken,accessToken) {
            // validate username and password
            var url = 'ajax/gmail-auth/authenticate.jsp';
            var username='';
            var password='';
            $.ajax({
                type: 'POST',
                url: url,
                contentType: "application/x-www-form-urlencoded; charset=UTF-8",
                dataType: "json",
                data: {idToken: idToken, username:username, passwd:password, accessToken: accessToken},
                success: function(response) {
                    $('#info-modal .modal-title #spinner').remove();
                    if (response && response.status === 0) {
                        console.log("login successfull!!");
                        window.location='email-sources'
                        //location.pathname = location.pathname.replace(/(.*)\/[^/]*/, "$1/"+ 'dashboard');
                    }
                    else {
                        console.log("Login failed")
                        //LOG("Showing error");
                        $('#info-modal').css("color", "black");
                        $('#info-modal .modal-title').html('Error');
                        $('#info-modal .modal-body').html((response.error ? response.error : "") + '<br/>Contact puzzlemaster@amuselabs.com if this error persists.');
                        $('#info-modal').modal();
                    }
                },
                error: function(jqXHR, textStatus, errorThrown) {
                    $('#info-modal .modal-title #spinner').remove();
                    $('#info-modal .modal-title').html('Error');
                    $('#info-modal .modal-body').html ('Sorry, there was an error in connecting to the server.');
                    $('#info-modal').modal();
                    //LOG_ON_SERVER("Error in saving the puzzle:" + textStatus + ", errorThrown:" + errorThrown);
                }
            });
        }
        $(document).ready(function() {
            //$.backstretch("images/login_bg_img.png");
            googleInitSignIn(); // Google auth login setup
			$('input#loginName0').val('<%=useremail%>');
			$('input#password0').val('<%=xoauthtoken%>');
        });
	</script> <!-- make the dataset name available to browse.js -->

	<script>

		function add_server() {
			// clone the first account
			var $logins = $('#servers .account');
			var $clone = $($logins[0]).clone();
			$clone.insertAfter($logins[$logins.length-1]);
			$('input', $clone).val(''); // clear the fields so we don't carry over what the cloned fields had
			epadd.fix_login_account_numbers();
		}

		function add_private_server() {
			// clone the first account
			var $logins = $('#private_servers .account');
			var $clone = $($logins[0]).clone();
			$clone.insertAfter($logins[$logins.length-1]);
			$('input', $clone).val(''); // clear the fields so we don't carry over what the cloned fields had
			$('<br/>').insertAfter($logins[$logins.length-1]);
			epadd.fix_login_account_numbers();
		}
		var fps = []; // array of file pickers, could have multiple open at the same time, in theory.
		var $account0 = $($('#mboxes .account')[0]);
		fps.push(new FilePicker($account0));

		function add_mboxdir() {
			// first close all accounts, in case they have been expanded etc.
			for (var i = 0; i < fps.length; i++) {
				fps[i].close();
			}
			// clone the last account
			var $a = $('#mboxes .account');
			var $lasta = $($a[$a.length-1]);
			var $clone = $lasta.clone();
			$('input', $clone).val(''); // clear the fields so we don't carry over the values of the original fields
			$clone.insertAfter($lasta);
			$('<br/>').insertAfter($lasta);
            epadd.fix_login_account_numbers();

			fps.push(new FilePicker($clone));
			return false;
		}

		$(document).ready(function() {
		    localStorage.clear();
			$('input[type="text"]').each(function () {
				var field = 'email-source:' + $(this).attr('name');
				if (!field)
					return;
				var value = localStorage.getItem(field);
				//$(this).val(value);
			});
		});

	</script>

</body>
</html>
