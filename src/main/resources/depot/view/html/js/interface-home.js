let body = $("body");
let repoOpsOnselect = $("#repo-ops-on-select");
let liRepoOpsRename = $("#li-repo-ops-rename");
let repoNav = $("#repo-nav");
let repoNavPath = $("#repo-nav-path");
let repoContentTable = $("#repo-content-table");
let repoContentCheckAll = $("#repo-content-check-all");
let repoContentTableBody = $("#repo-content-table > tbody");
let statusCount = $("#repo-content-status-count > span");
let colResizeData = {};
let colResizeStage = null;


function repoContentLoading(dataReloading = true) {
    repoOpsOnselect.hide();
    repoContentCheckAll.prop("checked", false);
    if (dataReloading) {
        switchRepoNavOps("repo-nav-ops-refresh", false);
        statusCount.html($("#repo-content-status-count-loading-tpl").html());
        repoContentTableBody.html($("#repo-content-table-loading-tpl").html());
    }
}

function switchRepoNavOps(opID, status) {
    let navOp = $("#" + opID);
    let disabledClass = "repo-nav-ops-disabled";
    if (status) {
        navOp.removeClass(disabledClass);
    } else {
        navOp.addClass(disabledClass);
    }
}

function repoNavPathNodeWidthTotal() {
    let sum = 0;
    repoNavPath.children().each(function () {
        sum += $(this).outerWidth(true);
    });
    return sum;
}

function adjustRepoNavPath() {
    tryRun(function () {
        let pathNodeList = javaApi.getPathNodeArray();
        repoNavPath.html(layui.laytpl($("#repo-nav-path-node-tpl").html()).render(pathNodeList));
        let pathNodeEllipsis = $("#repo-nav-path-node-ellipsis");
        pathNodeEllipsis.hide();
        while (repoNavPath.width() < repoNavPathNodeWidthTotal()) {
            pathNodeEllipsis.show();
            $("#repo-nav-path-node-flex").children().first().remove();
        }
    });
}

function loadRepoContent() {
    tryRun(function () {
        repoContentLoading();
        setTimeout(function () {
            javaApi.loadRepositoryContent();
        }, 1);
    });
}

function updateRepoNav() {
    tryRun(function () {
        switchRepoNavOps("repo-nav-ops-previous", javaApi.hasPrevious());
        switchRepoNavOps("repo-nav-ops-next", javaApi.hasNext());
        switchRepoNavOps("repo-nav-ops-parent", javaApi.hasParent());
        adjustRepoNavPath();
    });
}

function fillRepoContentTable() {
    tryRun(function () {
        let entryList = javaApi.getEntryArray();
        statusCount.html(entryList.length);
        if (entryList.length > 0) {
            repoContentTableBody.html(layui.laytpl($("#repo-content-table-tr-tpl").html()).render(entryList));
            resizeRepoContentTableColumn();
        } else {
            repoContentTableBody.html($("#repo-content-table-empty-tpl").html());
        }
    });
}

function showSortIcon(sortKey, direction) {
    $(".sort-icon").hide();
    $(".th-responsive[col-key='" + sortKey + "']").find(".sort-icon." + direction).show();
}

function sortEntryList(sortKey, direction) {
    let currentSortIcon = $(".sort-icon:visible");
    if (typeof (sortKey) == "undefined") {
        sortKey = "name";
        if (currentSortIcon.length > 0) {
            sortKey = currentSortIcon.closest(".th-responsive").attr("col-key");
        }
    }
    if (typeof (direction) == "undefined") {
        direction = "up";
        if (currentSortIcon.length > 0) {
            direction = currentSortIcon.hasClass("up") ? "up" : "down";
        }
    }
    tryRun(function () {
        repoContentLoading(false);
        javaApi.sortEntryList(sortKey, direction);
    });
}

function resizeRepoContentTableColumn() {
    $(".th-responsive").each(function () {
        let colKey = $(this).attr("col-key");
        let setWidth = parseInt($(this).css("width"));
        let columnItems = $("th[col-key='" + colKey + "'], td[col-key='" + colKey + "']");
        if (columnItems.length > 0) {
            columnItems.css("max-width", setWidth);
            columnItems.css("width", setWidth);
        }
    });
}

function allEntryChecked() {
    return $(".repo-content-check:not(:checked)").length <= 0;
}

function existingEntryCheckedNum() {
    return $(".repo-content-check:checked").not("#tr-new-dir *").length;
}

function noExistingEntryChecked() {
    return existingEntryCheckedNum() <= 0;
}

function getCheckedPaths() {
    let paths = [];
    $(".repo-content-check:checked").each(function () {
        let path = $(this).closest("tr").data("path");
        if (typeof (path) != "undefined") {
            paths.push(path);
        }
    });
    return paths;
}

function getDropTarget(x, y) {
    let target = {elem: null, pathString: null, name: null};
    let elem = document.elementFromPoint(x, y);
    let targetElem = $(elem).closest("tr.repo-content-tr-DIR:visible");
    if (targetElem.length > 0) {
        target.elem = targetElem;
        target.pathString = targetElem.data("path");
        target.name = targetElem.find(".repo-content-td-name").data("name");
        return target;
    }
    targetElem = $(elem).closest("#repo-content-table > tbody:visible");
    if (targetElem.length > 0) {
        target.elem = targetElem;
        target.pathString = window.currentParentPath;
        target.name = window.currentParentName;
        return target;
    }
    return target;
}

function dragOver(x, y, operation = "上传") {
    dragEnd();
    let target = getDropTarget(x, y);
    if (target.elem) {
        target.elem.addClass("drag-target");
    }
    if (target.pathString && target.name) {
        body.append($("<div></div>")
            .attr("id", "drag-tip")
            .css("left", x + 60)
            .css("top", y)
            .text(operation + "到\"" + target.name + "\""));
    }
}

function dragEnd() {
    $(".drag-target").removeClass("drag-target");
    $("#drag-tip").remove();
}

function entryNameVerified(name) {
    if (name.length > 255) {
        warn("名称长度大于255个字符");
        return false;
    }
    if (name === "." || name === "..") {
        warn("受保护的文件夹名称：" + name);
        return false;
    }
    let badCharacters = [
        '@', '#', '$', '%', '^', '*', '\b',
        '\t', '{', '}', '|', '\\',
        ':', ';', '"', '\n', '\r',
        '<', '>', ',', '?', '/',
    ];
    let result = true;
    $.each(badCharacters, function (idx, item) {
        if (result && name.indexOf(item) > 0) {
            warn("名称包含非法字符：" + item);
            result = false;
        }
    });
    return result;
}

function goPath(pathString) {
    tryRun(function () {
        javaApi.goPath(pathString);
    });
}

function goSearch() {
    let pattern = $("#repo-nav-search-pattern").val();
    tryRun(function () {
        javaApi.goSearch(pattern);
    });
}

function clearSearchPattern() {
    $("#repo-nav-search-pattern").val("");
}

function createDir(name) {
    tryRun(function () {
        repoContentLoading();
        javaApi.createDir(name);
    });
}

function downloadEntry(paths) {
    tryRun(function () {
        javaApi.downloadEntry(paths, paths.length);
    });
}

function deleteEntry(paths) {
    tryRun(function () {
        repoContentLoading();
        javaApi.deleteEntry(paths, paths.length);
    });
}

function renameEntry(pathString, newName) {
    tryRun(function () {
        repoContentLoading();
        javaApi.renameEntry(pathString, newName);
    });
}

function copyEntry(paths) {
    tryRun(function () {
        javaApi.copyEntry(paths, paths.length, false);
    });
}

function moveEntry(paths) {
    tryRun(function () {
        javaApi.copyEntry(paths, paths.length, true);
    });
}

function checkEntryDetail(paths) {
    tryRun(function () {
        javaApi.checkEntryDetail(paths, paths.length);
    });
}

/*上传*/
$(function () {
    $("#repo-ops-upload-dir").click(function () {
        tryRun(function () {
            javaApi.uploadDir();
        });
    });
    $("#repo-ops-upload-files").click(function () {
        tryRun(function () {
            javaApi.uploadFiles();
        });
    });
});

/*新建文件夹*/
$(function () {
    let trNewDirSelector = "#tr-new-dir";
    let tbodyDivFillSelector = ".repo-content-tbody-div-fill";

    $("#repo-ops-create-dir").click(function () {
        let trNewDir = $(trNewDirSelector);
        if (!trNewDir.length) {
            repoContentTableBody.find(tbodyDivFillSelector).hide();
            repoContentTableBody.prepend($("#repo-content-table-tr-new-dir-tpl").html());
            trNewDir = $(trNewDirSelector);
        }
        trNewDir.click();
    });

    repoContentTable.delegate(trNewDirSelector, "click", function (e) {
        e.stopPropagation();
        $("#new-dir-name").focus().select();
    });

    let commitNewDir = function () {
        let name = $("#new-dir-name").val();
        if (name.length > 0 && entryNameVerified(name)) {
            $(trNewDirSelector).remove();
            createDir(name);
        }
    };
    repoContentTable.delegate("#commit-new-dir", "click", function (e) {
        e.stopPropagation();
        commitNewDir();
    });
    repoContentTable.delegate("#new-dir-name", "keydown", function (e) {
        if (e.which === 13) {
            e.stopPropagation();
            commitNewDir();
        }
    });

    repoContentTable.delegate("#cancel-new-dir", "click", function (e) {
        e.stopPropagation();
        $(trNewDirSelector).remove();
        repoContentTableBody.find(tbodyDivFillSelector).show();
    });
    body.keydown(function (e) {
        if (e.which === 27) {
            $(trNewDirSelector).remove();
            repoContentTableBody.find(tbodyDivFillSelector).show();
        }
    });
});

/*下载*/
$(function () {
    $("#repo-ops-download-entry").click(function () {
        let paths = getCheckedPaths();
        if (paths.length > 0) {
            downloadEntry(paths);
        }
    });
});

/*删除*/
$(function () {
    $("#repo-ops-delete-entry").click(function () {
        let paths = getCheckedPaths();
        tryRun(function () {
            if (paths.length > 0) {
                let confirmMsg = "确定要删除" + paths.length + "个项目吗？";
                if (paths.length === 1) {
                    confirmMsg = "确定要删除“" + paths[0] + "”吗？";
                }
                if (javaApi.confirm(confirmMsg)) {
                    deleteEntry(paths);
                }
            }
        });
    });
});

/*重命名*/
$(function () {
    let trInRenamingId = "tr-in-renaming";
    let trInRenamingSelector = "#" + trInRenamingId;
    let spanCurrentNameSelector = ".current-name";
    let spanRenameSelector = ".span-rename";
    let inputNewNameSelector = "input[name='new-name']";

    $("#repo-ops-rename").click(function () {
        let existingEntryBoxChecked = $(".repo-content-check:checked").not("#tr-new-dir *");
        if (existingEntryBoxChecked.length === 1) {
            cancelRename();
            let trChecked = existingEntryBoxChecked.first().closest("tr");
            let currentName = trChecked.find(".repo-content-td-name").data("name");
            trChecked.attr("id", trInRenamingId);
            trChecked.find(spanCurrentNameSelector).hide();
            trChecked.find(inputNewNameSelector).val(currentName);
            trChecked.find(spanRenameSelector).show();
        }
        $(trInRenamingSelector).click();
    });

    repoContentTable.delegate(trInRenamingSelector, "click", function (e) {
        e.stopPropagation();
        $(this).find(inputNewNameSelector).focus().select();
    });

    let commitRename = function (trInRenaming) {
        if (typeof (trInRenaming) != "undefined") {
            let newName = trInRenaming.find(inputNewNameSelector).val();
            if (newName.length > 0 && entryNameVerified(newName)) {
                let pathString = trInRenaming.data("path");
                if (typeof (pathString) != "undefined") {
                    renameEntry(pathString, newName);
                }
            }
        }
    };
    repoContentTable.delegate(".commit-rename", "click", function (e) {
        e.stopPropagation();
        commitRename($(this).parents(trInRenamingSelector).first());
    });
    repoContentTable.delegate(trInRenamingSelector + " " + inputNewNameSelector, "keydown", function (e) {
        if (e.which === 13) {
            e.stopPropagation();
            commitRename($(this).parents(trInRenamingSelector).first());
        }
    });

    let cancelRename = function () {
        $(trInRenamingSelector).each(function () {
            $(this).find(spanRenameSelector).hide();
            $(this).find(spanCurrentNameSelector).show();
        }).removeAttr("id");
    };
    repoContentTable.delegate(".cancel-rename", "click", function (e) {
        e.stopPropagation();
        cancelRename();
    });
    body.keydown(function (e) {
        if (e.which === 27) {
            cancelRename();
        }
    });
});

/*移动/复制*/
$(function () {
    $("#repo-ops-copy").click(function () {
        let paths = getCheckedPaths();
        if (paths.length > 0) {
            copyEntry(paths);
        }
    });

    $("#repo-ops-move").click(function () {
        let paths = getCheckedPaths();
        if (paths.length > 0) {
            moveEntry(paths);
        }
    });
});

/*详细信息*/
$(function () {
    $("#repo-ops-check-detail").click(function () {
        let paths = getCheckedPaths();
        if (paths.length > 0) {
            checkEntryDetail(paths);
        }
    });
});

/*仓库管理*/
$(function () {
    $("#repo-ops-admin-compress").click(function () {
        tryRun(function () {
            javaApi.repoAdminCompress();
        });
    });

    $("#repo-ops-admin-verify").click(function () {
        tryRun(function () {
            javaApi.repoAdminVerify();
        });
    });
});

/*导航栏*/
$(function () {
    repoNav.delegate("#repo-nav-ops-previous:not(.repo-nav-ops-disabled)", "click", function () {
        tryRun(function () {
            javaApi.goPrevious();
        });
    });

    repoNav.delegate("#repo-nav-ops-next:not(.repo-nav-ops-disabled)", "click", function () {
        tryRun(function () {
            javaApi.goNext();
        });
    });

    repoNav.delegate("#repo-nav-ops-parent:not(.repo-nav-ops-disabled)", "click", function () {
        tryRun(function () {
            javaApi.goParent();
        });
    });

    repoNav.delegate("#repo-nav-ops-refresh:not(.repo-nav-ops-disabled)", "click", function () {
        loadRepoContent();
    });

    repoNav.delegate(".repo-nav-path-node", "click", function () {
        let pathString = $(this).data("path");
        if (typeof (pathString) != "undefined") {
            goPath(pathString);
        }
    });

    $(window).resize(function () {
        if (repoNav.is(":visible")) {
            adjustRepoNavPath();
        }
    });

    repoNav.delegate("#repo-nav-ops-search", "click", goSearch);

    repoNav.delegate("#repo-nav-search-pattern", "keydown", function (e) {
        if (e.which === 13) {
            e.stopPropagation();
            goSearch();
        }
    });
});

/*仓库文件列表*/
$(function () {
    repoContentTable.find("th").mousemove(function (e) {
        let oLeft = $(this).offset().left
            , pLeft = e.clientX - oLeft;
        if (!colResizeData.inResizing) {
            let selfResizable = $(this).hasClass("th-responsive");
            let prevResizable = $(this).prev().hasClass("th-responsive");
            let inSelfResizeRegion = selfResizable && $(this).innerWidth() - pLeft <= 10;
            let inPrevResizeRegion = prevResizable && pLeft <= 10;
            colResizeData.resizeReady = inSelfResizeRegion || inPrevResizeRegion;
            colResizeData.resizeTh = colResizeData.resizeReady && inSelfResizeRegion ? $(this) : $(this).prev();
            body.css("cursor", (colResizeData.resizeReady ? "col-resize" : ""));
        }
    }).mouseleave(function () {
        if (!colResizeData.inResizing) {
            body.css("cursor", "");
        }
    }).mousedown(function (e) {
        if (colResizeData.resizeReady) {
            let thWidthTotal = function () {
                let sum = 0;
                repoContentTable.find("thead > tr > th").each(function () {
                    sum += $(this).outerWidth(true);
                });
                return sum;
            };
            let colKey = colResizeData.resizeTh.attr("col-key");
            e.preventDefault();
            colResizeData.inResizing = true;
            colResizeData.initOffset = {x: e.clientX, y: e.clientY};
            colResizeData.resizingItems = $("th[col-key='" + colKey + "'], td[col-key='" + colKey + "']");
            colResizeData.minWidth = parseInt(colResizeData.resizeTh.css("min-width"));
            colResizeData.initWidth = parseInt(colResizeData.resizeTh.css("width"));
            colResizeData.initWidthTotal = thWidthTotal();
        }
    });

    let endResizing = function () {
        colResizeData = {};
        body.css("cursor", "");
    };
    body.mousemove(function (e) {
        if (colResizeData.inResizing) {
            if (e.which > 0) {
                e.preventDefault();
                if (colResizeData.resizingItems.length > 0) {
                    let theadWidth = repoContentTable.find("thead").innerWidth();
                    let widthDelta = Math.min(e.clientX - colResizeData.initOffset.x,
                        theadWidth - colResizeData.initWidthTotal);
                    let setWidth = Math.max(colResizeData.initWidth + widthDelta,
                        colResizeData.minWidth);
                    colResizeData.resizingItems.css("max-width", setWidth);
                    colResizeData.resizingItems.css("width", setWidth);
                }
                colResizeStage = 1;
            } else {
                endResizing();
            }
        }
    }).mouseup(function (e) {
        if (colResizeData.inResizing) {
            e.preventDefault();
            endResizing();
        }
        if (colResizeStage === 2) {
            colResizeStage = null;
        }
    });

    $(window).resize(function () {
        if (repoContentTable.is(":visible")) {
            resizeRepoContentTableColumn();
        }
    });

    repoContentTable.delegate(".th-responsive", "click", function () {
        if (colResizeStage === 1) {
            colResizeStage = 2;
        } else {
            let sortKey = $(this).attr("col-key");
            let downDefaultKeys = ["mtime", "size"];
            let direction = downDefaultKeys.indexOf(sortKey) < 0 ? "up" : "down";
            let currentSortIcon = $(this).find(".sort-icon:visible");
            if (currentSortIcon.length > 0) {
                direction = currentSortIcon.hasClass("up") ? "down" : "up";
            }
            sortEntryList(sortKey, direction);
        }
    });

    body.keydown(function (e) {
        if (e.ctrlKey && e.which === 65) {
            if ($("#sidebar-tab > .layui-this").attr("id") === "sidebar-repo-home") {
                $(".repo-content-check").prop("checked", true).change();
            }
        }
    });

    repoContentTable.delegate("#repo-content-check-all", "click", function (e) {
        e.stopPropagation();
        $(".repo-content-check").prop("checked", $(this).prop("checked")).change();
    });

    repoContentTable.delegate(".repo-content-check", "click", function (e) {
        e.stopPropagation();
        repoContentCheckAll.prop("checked", allEntryChecked());
    });

    repoContentTable.delegate(".repo-content-check", "change", function (e) {
        e.stopPropagation();
        let classSelected = "tr-selected";
        if ($(this).prop("checked")) {
            $(this).closest("tr").addClass(classSelected);
            if ($(this).closest("tr").attr("id") !== "tr-new-dir") {
                repoOpsOnselect.show();
            }
        } else {
            $(this).closest("tr").removeClass(classSelected);
            if (noExistingEntryChecked()) {
                repoOpsOnselect.hide();
            }
        }
        if (existingEntryCheckedNum() === 1) {
            liRepoOpsRename.show();
        } else {
            liRepoOpsRename.hide();
        }
        repoContentCheckAll.prop("checked", allEntryChecked());
    });

    repoContentTable.delegate("tbody > tr", "click", function (e) {
        e.stopPropagation();
        $(".repo-content-check").prop("checked", false).change();
        $(this).find(".repo-content-check").prop("checked", true).change();
    });

    repoContentTable.delegate("tbody > tr", "dblclick", function (e) {
        e.stopPropagation();
        $(this).find(".current-name:visible").click();
    });

    repoContentTable.delegate("tbody > tr.repo-content-tr-DIR .current-name:visible", "click", function (e) {
        e.stopPropagation();
        let path = $(this).closest("tr").data("path");
        if (typeof (path) != "undefined") {
            goPath(path);
        }
    });

    repoContentTable.delegate("tbody > tr.repo-content-tr-FILE .current-name:visible", "click", function (e) {
        e.stopPropagation();
        let path = $(this).closest("tr").data("path");
        if (typeof (path) != "undefined") {
            downloadEntry([path]);
        }
    });
});
