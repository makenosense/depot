let logTreeOptions = {
    core: {
        themes: {
            name: "default-dark",
            dots: false,
        },
        animation: false,
        dblclick_toggle: false,
    },
    types: treeNodeTypes,
    sort: treeNodeCompare,
    plugins: ["types", "sort"],
};


function loadRepoLog(rebuild = false) {
    tryRun(function () {
        javaApi.loadRepositoryLog(rebuild);
    });
}

function setLogCacheRefreshingTime(lastRefreshingTime) {
    $("#log-last-refreshing-time").text(lastRefreshingTime);
}

function createLogTree() {
    tryRun(function () {
        logTreeOptions.core.data = function (node, callback) {
            let children = [];
            $.each(javaApi.getLogTreeNodeChildrenArray(node.id), function (idx, treeNode) {
                children.push({
                    id: treeNode.id,
                    parent: treeNode.parent,
                    type: treeNode.type,
                    text: treeNode.text
                        + (treeNode.comment.length > 0 ?
                            $("<span></span>")
                                .addClass("path-comment")
                                .text(" - " + treeNode.comment)
                                .prop("outerHTML") : ""),
                    state: {
                        opened: treeNode.state.opened,
                    },
                    children: treeNode.children,
                });
            });
            callback.call(this, children);
        };
        destroyLogTree();
        $("#log-tree").jstree(logTreeOptions).on("select_node.jstree", function (e, data) {
            let logTree = getLogTree();
            logTree.deselect_node(data.node);
            logTree.toggle_node(data.node);
        });
    });
}

function getLogTree() {
    return $.jstree.reference("#log-tree");
}

function destroyLogTree() {
    let logTree = getLogTree();
    if (logTree != null) {
        logTree.destroy();
    }
}

function expandAllLogTreeNodes() {
    let logTree = getLogTree();
    if (logTree != null) {
        logTree.open_all();
    }
}

function collapseAllLogTreeNodes() {
    let logTree = getLogTree();
    if (logTree != null) {
        logTree.close_all();
    }
}
