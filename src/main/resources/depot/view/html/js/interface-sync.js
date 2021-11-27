let syncRepoBody = $("#sync-repo-body");
let syncSourceList = $("#sync-source-list");
let syncTargetList = $("#sync-target-list");
let syncRepoSelectedClass = "sync-repo-selected";

function switchSyncOps(enable) {
    if (enable) {
        $("#sync-ops button").removeClass("layui-btn-disabled")
            .prop("disabled", false);
    } else {
        $("#sync-ops button").addClass("layui-btn-disabled")
            .prop("disabled", true);
    }
}

function loadSyncRepoList() {
    syncSourceList.empty();
    syncTargetList.empty();
    tryRun(function () {
        let baiduPanConfig = javaApi.loadBaiduPanConfig();
        let baiduPanListItemTpl = $("#baidu-pan-list-item-tpl").html();
        syncSourceList.html(layui.laytpl(baiduPanListItemTpl).render(baiduPanConfig));
        syncTargetList.html(layui.laytpl(baiduPanListItemTpl).render(baiduPanConfig));
        let syncRepoConfigList = javaApi.loadSyncRepoConfigList();
        if (syncRepoConfigList.length > 0) {
            let listItemTpl = $("#sync-repo-list-item-tpl").html();
            syncSourceList.append(layui.laytpl(listItemTpl).render(syncRepoConfigList));
            syncTargetList.append(layui.laytpl(listItemTpl).render(syncRepoConfigList));
        }
        switchSyncOps(false);
    });
}

function getSourceUUID() {
    return syncSourceList.find("." + syncRepoSelectedClass).first().data("uuid");
}

function getTargetUUID() {
    return syncTargetList.find("." + syncRepoSelectedClass).first().data("uuid");
}

function syncCandidateReady() {
    let sourceUUID = getSourceUUID();
    let targetUUID = getTargetUUID();
    return typeof (sourceUUID) != "undefined"
        && typeof (targetUUID) != "undefined"
        && sourceUUID !== targetUUID;
}

function syncRepository(sourceUUID, targetUUID) {
    tryRun(function () {
        javaApi.syncRepository(sourceUUID, targetUUID);
    });
}

function compareRepository(sourceUUID, targetUUID) {
    tryRun(function () {
        javaApi.compareRepository(sourceUUID, targetUUID);
    });
}

$(function () {
    syncRepoBody.delegate(".baidu-pan-ops-sign-in", "click", function (e) {
        e.stopPropagation();
        tryRun(function () {
            javaApi.baiduPanSignIn();
        });
    });

    syncRepoBody.delegate(".baidu-pan-ops-change-root-path", "click", function (e) {
        e.stopPropagation();
        tryRun(function () {
            javaApi.baiduPanChangeRootPath();
        });
    });

    syncRepoBody.delegate(".baidu-pan-ops-sign-out", "click", function (e) {
        e.stopPropagation();
        tryRun(function () {
            javaApi.baiduPanSignOut();
        });
    });

    syncRepoBody.delegate(".sync-repo-list-item", "click", function () {
        $(this).closest(".sync-repo-list")
            .find("." + syncRepoSelectedClass).removeClass(syncRepoSelectedClass);
        $(this).addClass(syncRepoSelectedClass);
        switchSyncOps(syncCandidateReady());
    });

    $("#sync-ops-sync").click(function () {
        let sourceUUID = getSourceUUID();
        let targetUUID = getTargetUUID();
        if (syncCandidateReady()) {
            syncRepository(sourceUUID, targetUUID);
        }
    });

    $("#sync-ops-compare").click(function () {
        let sourceUUID = getSourceUUID();
        let targetUUID = getTargetUUID();
        if (syncCandidateReady()) {
            compareRepository(sourceUUID, targetUUID);
        }
    });
});
