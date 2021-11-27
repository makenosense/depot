layui.use(["element", "form", "laytpl"], function () {
    const element = layui.element;
    const form = layui.form;

    element.tab({
        headerElem: "#sidebar-tab>.sidebar-tab-item",
        bodyElem: "#content>.content-item",
    });

    element.on("tab(sidebar-tab)", function (data) {
        let sidebarTabId = data.elem.prevObject.attr("id");
        if (sidebarTabId === "sidebar-repo-home") {
            loadRepoContent();
        } else if (sidebarTabId === "sidebar-repo-log") {
            loadRepoLog();
        } else if (sidebarTabId === "sidebar-repo-sync") {
            loadSyncRepoList();
        } else if (sidebarTabId === "sidebar-settings") {
            loadAppSettings();
        }
    });

    form.on("submit(form-settings-submit)", function (data) {
        tryRun(function () {
            javaApi.saveAppSettings(data.field);
        });
        return false;
    });

    $("#sidebar-repo-close").click(function () {
        tryRun(function () {
            javaApi.closeRepository();
        });
    });
});
