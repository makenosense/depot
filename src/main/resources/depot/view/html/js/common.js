function warn(msg) {
    javaApi.warn(msg);
}

function logError(error) {
    try {
        javaApi.error(error.toString());
    } finally {
        console.log(error);
    }
}

function tryRun(func) {
    try {
        func();
    } catch (error) {
        logError(error);
    }
}

function switchToSidebarTab(sidebarTabId) {
    $("#sidebar-tab").find("#" + sidebarTabId).click();
}

function switchToSidebarTabWithDelay(sidebarTabId, delay = 100) {
    setTimeout(function () {
        switchToSidebarTab(sidebarTabId);
    }, delay);
}

let treeNodeTypes = {
    date: {icon: "fas fa-calendar-alt", li_attr: {class: "date"}},
    revision: {icon: "fas fa-history", li_attr: {class: "revision"}},
    dir: {icon: "fas fa-folder", li_attr: {class: "dir"}},
    dir_a: {icon: "fas fa-folder", li_attr: {class: "added"}},
    dir_m: {icon: "fas fa-folder", li_attr: {class: "modified"}},
    dir_d: {icon: "fas fa-folder", li_attr: {class: "deleted"}},
    file_a: {icon: "far fa-file", li_attr: {class: "added"}},
    file_m: {icon: "far fa-file", li_attr: {class: "modified"}},
    file_d: {icon: "far fa-file", li_attr: {class: "deleted"}},
};
let treeNodeCompare = function (n1, n2) {
    let type1 = this.get_type(n1), type2 = this.get_type(n2);
    let text1 = this.get_text(n1), text2 = this.get_text(n2);
    let startsWithLetterOrDigit = /^\w/;
    let textCompareResult = text1.localeCompare(text2, undefined, {sensitivity: "base"});
    if (startsWithLetterOrDigit.test(text1) || startsWithLetterOrDigit.test(text2)) {
        textCompareResult = text1.localeCompare(text2, "en", {sensitivity: "base"});
    }
    if ((type1 === "date" && type2 === "date")
        || (type1 === "revision" && type2 === "revision")) {
        return -textCompareResult;
    } else if (type1.startsWith("dir")) {
        return type2.startsWith("dir") ? textCompareResult : -1;
    } else {
        return !type2.startsWith("dir") ? textCompareResult : 1;
    }
};
