
// global vars, used by every module below

var docIDs = []; // this is required for posting the docid of the message to which the label/annotation change should be applied.
var PAGE_ON_SCREEN = -1; // current page displayed on screen
var TOTAL_PAGES = 0;
var JOG_URL_PREFIX;
var JOG_URL_PRESERVATION = JOG_URL_PREFIX + "1";
var JOG_URL_NON_PRESERVATION = JOG_URL_PREFIX + "0";
var MESSAGE_HAVE_REDACTED = false;   // this is a flag to indicate current message on screen has been redacted.

// interacts with #page_forward, #page_back, and #pageNumbering on screen
var Navigation = function(){

    var jog;
    // currently called before the new page has been rendered, private method
    var page_change_callback = function(oldPage, currentPage) {

        PAGE_ON_SCREEN = currentPage;
        $('#pageNumbering').html(((TOTAL_PAGES === 0) ? 0 : currentPage+1));
        Labels.refreshLabels();
        Annotations.refreshAnnotation();

        // update the links
        // $('.message-menu a.id-link').attr('href', 'browse?archiveID=' + archiveID + '&docId=' + window.messageMetadata[PAGE_ON_SCREEN].id);
        // we need a full link here since this has to be a persistent URL
        // window.location.origin gives us something like http://localhost:9099
        $('.message-menu a.id-link').attr('data-href', window.location.origin + '/epadd/browse?archiveID=' + archiveID + '&docId=' + window.messageMetadata[PAGE_ON_SCREEN].id);
        $('.message-menu a.thread-link').attr('href', 'browse?archiveID=' + archiveID + '&threadID=' + window.messageMetadata[PAGE_ON_SCREEN].threadID);
        $('.message-menu a.thread-link .thread-count').text(window.messageMetadata[PAGE_ON_SCREEN].msgInThread);
        $('.message-menu .attach-link span').html(window.messageMetadata[PAGE_ON_SCREEN].nAttachments);
        /*if(window.messageMetadata[PAGE_ON_SCREEN].annotation || Annotations[PAGE_ON_SCREEN]) {
            //change the image of add-annotation if this message has annotation. Because we want to display that icon with a green dot if the message has an annotation.
            $('.message-menu a.annotation-link img').attr('src','images/add_annotation_dot.svg');
           // $('.message-menu a.annotation-link .image').attr('src', "images/add_annotation_dot.svg");
        }else{
            $('.message-menu a.annotation-link img').attr('src','images/add_annotation.svg');
        }*/

    };

    var setupEvents = function() {
        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        //Big gotcha here: Be very careful what method is passed as logger into this jog method.
        //if for some reason, the logger fails or actually does a post operation; this thing pushes
        // it to retry making the whole thing (the entire browser and the system) to slow down
        //TODO: JOG plugin should not be this aggressive with the logger
        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

        JOG_URL_PREFIX = 'ajax/jogPageInMessages.jsp?archiveID=' + archiveID + '&datasetId=' + docsetID + '&preserveMode=';
        JOG_URL_PRESERVATION = JOG_URL_PREFIX + "1";
        JOG_URL_NON_PRESERVATION = JOG_URL_PREFIX + "0";

        var cookie = Cookies.get('palladium-epadd-mode-preserve');
        var currentJogURL = (cookie && cookie == "1")? JOG_URL_PRESERVATION : JOG_URL_NON_PRESERVATION;

        jog = $(document).jog({
            paging_info: {
                //url: 'ajax/jogPageInMessages.jsp?archiveID=' + archiveID + '&datasetId=' + docsetID + '&preserveMode=0',
                url: currentJogURL,
                window_size_back: 30,
                window_size_fwd: 50
            },
            page_change_callback: page_change_callback,
            logger: epadd.log,
            width: 180,
            /* enable this to enable jog dial
            disabled: false,
            dynamic: true
            */
            disabled: true,
            dynamic: false
        });

        // forward/back nav
        $('#page_forward').click(jog.forward);
        $('#page_back').click(jog.backward);
    };

    // annoying, have to declare these connector funcs, because jog is defined only in setupEvents, so can't directly use those funcs in the Navigation's interface.
    function disableCursorKeys() { jog.disableCursorKeys();}
    function enableCursorKeys() { jog.enableCursorKeys();}
    function reloadCurrentPage() { jog.reloadCurrentPage();}
    function reloadJogURL(url) {jog.reloadJogURL(url);}

    return {
        setupEvents: setupEvents,
        disableCursorKeys: disableCursorKeys,
        enableCursorKeys: enableCursorKeys,
        reloadCurrentPage: reloadCurrentPage,
        reloadJogURL: reloadJogURL
    };
}();


// interacts with .
// label-selectpicker and .labels-area on screen
var Labels = function() {
    var labelsOnPage = []; // private to this module
    var currentPageOldLabels;

    /** private method labelIds is an array of label ids (e.g. [1,2,5]) which are to be applied to the current message */
    function apply_labels(labelIds) {
        //
        // if(labelIds.length===0){
        //     return;
        // }

        // post to the backend, and when successful, refresh the labels on screen
        $.ajax({
            url: 'ajax/applyLabelsAnnotations.jsp',
            type: 'POST',
            data: {archiveID: archiveID, docId: docIDs[PAGE_ON_SCREEN], labelIDs: labelIds.join(), action: "override"}, // labels will go as CSVs: "0,1,2" or "id1,id2,id3"
            dataType: 'json',
            success: function (response) {
                if(response.status===1) {
                    epadd.error(response.errorMessage);
                    labelsOnPage[PAGE_ON_SCREEN] = currentPageOldLabels;
                }
                refreshLabels();//otherwise the selected checkboxes and onscreen labels are not getting reset to the old labels [old means the labels before the erroneous labels were set]
            },
            error: function () {
                epadd.error('There was an error saving the annotation. Please try again, and if the error persists, report it to epadd_project@stanford.edu.');
            }
        });
    }

    // redraws labels for PAGE_ON_SCREEN
    var refreshLabels = function () {

        var labelIds = labelsOnPage[PAGE_ON_SCREEN];

        // set the dropdown according to the labels onthis message
        $('.label-selectpicker').selectpicker ('val', labelIds ? labelIds : ""); // e.g. setting val, [0, 1, 3] will set the selectpicker state to these 3

        $('.labels-area').html(''); // wipe out existing labels

        if (!labelIds)
            return;

        for (var i = 0; i < labelIds.length; i++) {
            var label_id = labelIds[i];
            var label = allLabels[label_id];
            if (!label)
                continue;

            var class_for_label; // this is one of system/general/restriction label
            {
                if (label.labType === 'RESTRICTION')
                    class_for_label = 'restriction-label';
                else if (label.labType === 'GENERAL')
                    class_for_label = 'general-label';

                if (label.isSysLabel & label.labId!=2)
                    class_for_label += ' system-label';
                else
                    class_for_label += ' system-label cfr-label';
            }

            // restriction + system labels will have both system-label and restr. label applied and will be colored red
            // non-system restriction labels will be colored orange
            // general labels will be colored blue
            $('.labels-area').append(
                '<div '
                + ' data-label-id="' + label.labId + '" '
                + ' title="' + escapeHTML(label.description) + '" '
                + ' class="message-label ' + class_for_label + '" >'
                + escapeHTML(label.labName)
                + '</div>');
        }
    };

    var setup = function () {

        // set up docIDs and labelsOnPage
        for (var i = 0; i < TOTAL_PAGES; i++) {
            labelsOnPage[i] = messageMetadata[i].labels;
            docIDs[i] = messageMetadata[i].id;
        }

        // set up label handling
        $('.label-selectpicker').on('change', function () {
            var labelIds = $('.label-selectpicker').selectpicker('val') || [];
            if (labelIds) {
                currentPageOldLabels = labelsOnPage[PAGE_ON_SCREEN];
                labelsOnPage[PAGE_ON_SCREEN] = labelIds;
                apply_labels(labelIds);
            }
        });
    };

    return {
        setup: setup,
        refreshLabels: refreshLabels
    };
}();

// interacts with #annotation-modal and .annotation-area on screen
// posts to applyLabelsAnnotations.jsp on updates
var Annotations = function() {

    var annotations = [];

    // set up event handlers for annotations, should be called only once
    function setup() {

        // things to do when annotation modal is shown
        function annotation_modal_shown() {
            $('#annotation-modal .modal-body').val(annotations[PAGE_ON_SCREEN]).focus();
            Navigation.disableCursorKeys();
        }

        // things to do when user clicks on 'apply to this message'
        function annotation_modal_dismissed_apply_to_this_message () {
            var overwrite_or_append = $('#annotation-modal input[type=radio]:checked').attr('value')

            Navigation.enableCursorKeys();
            var annotation = $('#annotation-modal .modal-body').val().trim(); // .val() gets the value of a text area. assume: no html in annotations
            if(overwrite_or_append=="overwrite") {
                    annotations[PAGE_ON_SCREEN] = annotation;
            }else if (overwrite_or_append=="append"){
                    annotations[PAGE_ON_SCREEN] += annotation;
            }

            Annotations.refreshAnnotation();

            // post to the backend, and when successful, refresh the labels on screen
            $.ajax({
                url: 'ajax/applyLabelsAnnotations.jsp',
                type: 'POST',
                data: {
                    archiveID: archiveID,
                    docId: docIDs[PAGE_ON_SCREEN],
                    annotation: annotation,
                    action: overwrite_or_append
                }, // labels will go as CSVs: "0,1,2" or "id1,id2,id3"
                dataType: 'json',
                success: function (response) {
                    if(overwrite_or_append=="overwrite")
                        $('.annotation-area').text(annotation ? annotation: 'No annotation'); // we can't set the annotation area to a completely empty string because it messes up rendering if the span is empty!
                    else if(overwrite_or_append=="append")
                        $('.annotation-area').text(annotation ? annotations[PAGE_ON_SCREEN]: 'No annotation');
                    //$('.annotation-area').text(annotation ? annotation: 'No annotation'); // we can't set the annotation area to a completely empty string because it messes up rendering if the span is empty!
                  //  $('#annotation-modal .modal-body').val(''); // clear the val otherwise it briefly appears the next time the annotation modal is invoked
                },
                error: function () { epadd.error('There was an error saving the annotation. Please try again, and if the error persists, report it to epadd_project@stanford.edu.');}
            });
        }


        // things to do when user clicks on 'apply to all messages'
        function annotation_modal_dismissed_apply_to_all_messages() {
            //ask user that 'all existing annotations on selected # of messages will be overwritten. Do you want to continue?'
            var overwrite_or_append = $('#annotation-modal input[type=radio]:checked').attr('value')
            if (!overwrite_or_append)
            {
                alert("Select an option to either ovewrite or append this annotation");
            return false;
            }
            if(overwrite_or_append=="overwrite") {
                var c = epadd.warn_confirm_continue('The existing annotations for all ' + numMessages + ' messages will be overwritten. Do you want to continue?', function() {
                    //If user confirms then proceed.
                    Navigation.enableCursorKeys();
                    var annotation = $('#annotation-modal .modal-body').val().trim(); // .val() gets the value of a text area. assume: no html in annotations
                    // if(annotation.trim().length==0)
                    //     annotation="";
                    if(overwrite_or_append=="overwrite") {
                        for (var i = 0; i < TOTAL_PAGES; i++)
                            annotations[i] = annotation;
                    }else if (overwrite_or_append=="append"){
                        for (var i = 0; i < TOTAL_PAGES; i++)
                            annotations[i] += annotation;
                    }

                    Annotations.refreshAnnotation();

                    // post to the backend, and when successful, refresh the labels on screen
                    $.ajax({
                        url: 'ajax/applyLabelsAnnotations.jsp',
                        type: 'POST',
                        data: {
                            archiveID: archiveID,
                            docsetID: docsetID,
                            annotation: annotation,
                            action: overwrite_or_append
                        },
                        dataType: 'json',
                        success: function (response) {
                            if(overwrite_or_append=="overwrite")
                                $('.annotation-area').text(annotation ? annotation: 'No annotation'); // we can't set the annotation area to a completely empty string because it messes up rendering if the span is empty!
                            else if(overwrite_or_append=="append")
                                $('.annotation-area').text(annotation ? annotations[PAGE_ON_SCREEN]: 'No annotation');
                            //  $('#annotation-modal .modal-body').val(''); // clear the val otherwise it briefly appears the next time the annotation modal is invoked
                        },
                        error: function () { epadd.error('There was an error saving the annotation. Please try again, and if the error persists, report it to epadd_project@stanford.edu.');}
                    });
                });
            }
        }

        for (var i = 0; i < TOTAL_PAGES; i++) {
            annotations[i] = messageMetadata[i].annotation;
            if (annotations[i] === null) // protect against null, otherwise the word null uglily (q: is that a word? probably fine. its a better word than bigly.) appears on screen.
                annotations[i] = '';
        }

        // set up handlers for when annotation modal is shown/dismissed
        //$('#annotation-modal').on('shown.bs.modal', annotation_modal_shown).on('hidden.bs.modal', annotation_modal_dismissed);
        //set up handlers when different buttons are clicked on annotation modal. For 'Apply to this message' invoke different handler,
        //For 'Apply to all messages' invoke another handler. When modal is dismissed, by default the behaviour will be nothing.
        $('#annotation-modal').on('shown.bs.modal', annotation_modal_shown);
        $('#annotation-modal').on('hidden.bs.modal', function() { Navigation.enableCursorKeys(); $('.annotation-area').css('filter', ''); /* clear the blur effect */});

        $('#annotation-modal').find('#ok-button-annotations').click(annotation_modal_dismissed_apply_to_this_message);
        $('#annotation-modal').find('#apply-all-button').click(annotation_modal_dismissed_apply_to_all_messages);

        // when annotation is clicked, invoke modal
        $('a.annotation-link').click(function () {
            // show the modal
            $('div.annotation').show();
            $('#annotation-modal').modal();
            $('.annotation-area').css('filter','blur(2px)')
            return false;
        });
    }

    // copies annotation from .annotation-area on screen to the current page's
    function refreshAnnotation() {
        var $annotation = $('div.annotation');
        var $annotationarea = $('.annotation-area', $annotation);
        $('.annotation-area').css('filter','');

        if (annotations[PAGE_ON_SCREEN] && annotations[PAGE_ON_SCREEN].length > 0) {
            $annotationarea.text(annotations[PAGE_ON_SCREEN]);
            $annotation.show();
            //change the UI
            $('.message-menu a.annotation-link img').attr('src','images/add_annotation_dot.svg');
        } else {
            $annotationarea.text('No annotation');
            $annotation.hide();
            //chage the UI
            $('.message-menu a.annotation-link img').attr('src','images/add_annotation.svg');
        }
    }

    return {
        setup: setup,
        refreshAnnotation: refreshAnnotation,
        annotations: annotations // debug only
    };
}();




var emailModifications = function() {

    modifiedEmail = '';
    // set up event handlers for annotations, should be called only once
    function setup() {

        // things to do when modification modal is shown
        function email_modification_modal_shown() {

            //console.log('PAGE_ON_SCREEN='+ PAGE_ON_SCREEN + ' || docIDs[PAGE_ON_SCREEN]='+ docsetID + ' || archiveID=' + archiveID );

            // post to the backend, and when successful, refresh the text area with email body content on screen
            $.ajax({
                    url : 'ajax/getBodyContentForEdit.jsp',
                    type : 'POST',

                    data : {
                            archiveID: archiveID,
                            docsetID: docsetID,
                            currentPage : PAGE_ON_SCREEN,
                    },

                    dataType : 'json',
                    success : function(response) {

                                if (response && response.status==0 ) {
						            $('#email-modification-modal .modal-body').val(response.emailBody);
                                }
                    },
                    error : function() {
                            epadd
                            .error('There was an error loading the email body for email modification. Please try again, and if the error persists, report it to epadd_project@stanford.edu.');
                    }
            });

            $('#email-modification-modal .modal-body').focus();

            Navigation.disableCursorKeys();
        }

        // things to do when user clicks on 'apply to this message'
        function email_modification_modal_dismissed_apply_to_this_message() {
            
            Navigation.enableCursorKeys();
            modifiedEmail = $('#email-modification-modal .modal-body').val().trim(); // .val() gets the value of a text area. assume: no html in annotations

            // post to the backend, and when successful, refresh the labels on screen
            $.ajax({
                        url : 'ajax/applyEmailModification.jsp',
                        type : 'POST',
                        data : {
                            archiveID : archiveID,
                            docId : docIDs[PAGE_ON_SCREEN],
                            modifiedEmail : modifiedEmail,
                        }, 
                        dataType : 'json',
                        success : function(response) {
                            emailModifications.refreshEmail();
                        },
                        error : function() {
                            epadd
                                    .error('There was an error saving the modified email. Please try again, and if the error persists, report it to epadd_project@stanford.edu.');
                        }
                    });
        }

        $('#email-modification-modal').on('shown.bs.modal', email_modification_modal_shown);
        $('#email-modification-modal').on('hidden.bs.modal', function() {
            Navigation.enableCursorKeys();
            $('#email-modification-modal .modal-body').val(''); // reset the email modification modal to empty before next call
        });

        $('#email-modification-modal').find('#ok-button-email-modification').click(
                email_modification_modal_dismissed_apply_to_this_message);
            // when annotation is clicked, invoke modal
        $('a.email-content-link').click(function() {
            // show the modal
            $('div.email-modification').show();
            $('#email-modification-modal').modal();
            return false;
        });

        // when preservation is clicked, trigger navigation mode between preservation and non-preservation
        // it is based on the existence of session cookie stored as 'palladium-epadd-mode-preserve'
        var cookie = Cookies.get('palladium-epadd-mode-preserve');

        $('#trigger-preservation').click(function() {
            // Toggle mode between preservation and normal navigation modes
            var newMode = (cookie == null || cookie == '0') ? true : false;     // true for Preservation mode, false for normal navigation mode

            setup_upon_mode_switching(newMode);
            window.location.reload();       // Due to some reason, upon view mode change, we have to reload the whole window screen to force refresh the jog plugin cached memory
        });

        // do some setup tasks for UI icons, toggle mode button and edit icon appearance etc.
        if ( cookie == null || cookie == '0'){
            // Toggle UI to normal navigation view
            $('#plock').attr('src', 'images/lock-0.svg' );
            $('#plock').attr('title', 'Toggle to perservation view' );
            $('#img-edit').show();
        } else {
            // Toggle UI to preservation view
            $('#plock').attr('src', 'images/lock-1.svg' );
            $('#plock').attr('title', 'Toggle to normal view' );
            $('#img-edit').hide();
        }

    }  // end Setup()

    function reloadJogURL(url){
        Navigation.reloadJogURL(url);
    }

    // copies annotation from .annotation-area on screen to the current page's
    function refreshEmail() {
        Navigation.reloadCurrentPage();

        //window.location.reload();
//        var $annotation = $('div.annotation');
//        var $annotationarea = $('.annotation-area', $annotation);
//        $('.annotation-area').css('filter', '');
//
//        if (annotations[PAGE_ON_SCREEN]
//                && annotations[PAGE_ON_SCREEN].length > 0) {
//            $annotationarea.text(annotations[PAGE_ON_SCREEN]);
//            $annotation.show();
//            //change the UI
//            $('.message-menu a.annotation-link img').attr('src',
//                    'images/add_annotation_dot.svg');
//        } else {
//            $annotationarea.text('No annotation');
//            $annotation.hide();
//            //chage the UI
//            $('.message-menu a.annotation-link img').attr('src',
//                    'images/add_annotation.svg');
//        }
    }

    function setup_upon_mode_switching(inPreserveMode) {

        if (inPreserveMode) {
            // setup UI for preservation mode
            $('#plock').attr('src', 'images/lock-1.svg' );
            $('#plock').attr('title', 'Toggle to normal view' );
            $('#img-edit').hide();
            reloadJogURL(JOG_URL_PRESERVATION);
            Cookies.set('palladium-epadd-mode-preserve', '1');
        } else {
            // setup UI for normal navigation mode
            $('#plock').attr('src', 'images/lock-0.svg' );
            $('#plock').attr('title', 'Toggle to preservation view' );
            $('#img-edit').show();
            reloadJogURL(JOG_URL_NON_PRESERVATION);
            Cookies.set('palladium-epadd-mode-preserve', '0');
        }

        //refresh the jog view
        emailModifications.refreshEmail();

        return false;
    }

    return {
        setup : setup,
        refreshEmail : refreshEmail,
        modifiedEmail : modifiedEmail,
        reloadJogURL: reloadJogURL,
        setup_upon_mode_switching: setup_upon_mode_switching
    // debug only
    };
}();

$(document).ready(function() {

    // allow facets panel to run the full height of the screen on the left
    $('div.facets').css('min-height', window.innerHeight - 50);

    PAGE_ON_SCREEN = 0;
    Labels.setup();
    Annotations.setup();
    emailModifications.setup();
    Navigation.setupEvents(); // important -- this has to be after labels and annotations setup to render the first page correctly

    // on page unload, release dataset to free memory
    $(window).unload(function () {
        epadd.log('releasing dataset ' + docsetID);
        $.get('ajax/releaseDataset.jsp?docsetID=' + docsetID);
    });
});
