let formSettings = $("#form-settings");
let formSettingsAction = formSettings.find(".footer-action-bar button");

function loadAppSettings() {
    tryRun(function () {
        let settings = javaApi.loadAppSettings();
        setDownloadParent(settings.getDownloadParent().getAbsolutePath());
        formSettingsAction.addClass("layui-btn-disabled")
            .prop("disabled", true);
    });
}

function setDownloadParent(downloadParent, enableSave = false) {
    formSettings.find("input[name='downloadParent']").val(downloadParent);
    if (enableSave) {
        formSettingsAction.removeClass("layui-btn-disabled")
            .prop("disabled", false);
    }
}

$(function () {
    $("#icon-choose-downloadParent").click(function () {
        tryRun(function () {
            javaApi.chooseDownloadParent();
        });
    });

    $("#form-settings-reset").click(loadAppSettings);
});
