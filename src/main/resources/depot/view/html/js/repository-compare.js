let compareTreeOptions = {
    core: {
        themes: {
            name: "default-dark",
            dots: false,
        },
        multiple: false,
        animation: false,
        dblclick_toggle: false,
    },
    types: treeNodeTypes,
    sort: treeNodeCompare,
    plugins: ["types", "sort"],
};
let propSize = "SIZE";
let propMtime = "MTIME";
let propChecksum = "CHECKSUM";


function renewCompareResult() {
    tryRun(function () {
        javaApi.renewCompareResult();
    });
}

function createCompareTree() {
    tryRun(function () {
        let data = [];
        $.each(javaApi.getCompareTreeNodeArray(), function (idx, treeNode) {
            data.push({
                id: treeNode.id,
                parent: treeNode.parent,
                type: treeNode.type,
                text: treeNode.text
                    + (treeNode.comment.length > 0 ?
                        $("<span></span>")
                            .addClass("path-comment")
                            .text(" " + treeNode.comment)
                            .prop("outerHTML") : ""),
                state: {
                    opened: treeNode.state.opened,
                },
                data: {
                    sourceProp: {
                        size: treeNode.getSourceProperty(propSize),
                        mtime: treeNode.getSourceProperty(propMtime),
                        checksum: treeNode.getSourceProperty(propChecksum),
                    },
                    targetProp: {
                        size: treeNode.getTargetProperty(propSize),
                        mtime: treeNode.getTargetProperty(propMtime),
                        checksum: treeNode.getTargetProperty(propChecksum),
                    },
                },
            });
        });
        compareTreeOptions.core.data = data;
        destroyCompareTree();
        $("#compare-tree").jstree(compareTreeOptions).on("select_node.jstree", function (e, data) {
            let sourceProp = data.node.data.sourceProp;
            let targetProp = data.node.data.targetProp;
            let safeOutput = function (o) {
                return o != null ? o : "-";
            };
            $("#compare-source-prop .prop-size").text(safeOutput(sourceProp.size));
            $("#compare-source-prop .prop-mtime").text(safeOutput(sourceProp.mtime));
            $("#compare-source-prop .prop-checksum").text(safeOutput(sourceProp.checksum));
            $("#compare-target-prop .prop-size").text(safeOutput(targetProp.size));
            $("#compare-target-prop .prop-mtime").text(safeOutput(targetProp.mtime));
            $("#compare-target-prop .prop-checksum").text(safeOutput(targetProp.checksum));
        });
    });
}

function createCompareTreeWithDelay(delay = 100) {
    setTimeout(createCompareTree, delay);
}

function getCompareTree() {
    return $.jstree.reference("#compare-tree");
}

function destroyCompareTree() {
    let compareTree = getCompareTree();
    if (compareTree != null) {
        compareTree.destroy();
    }
}

function expandAllCompareTreeNodes() {
    let compareTree = getCompareTree();
    if (compareTree != null) {
        compareTree.open_all();
    }
}

function collapseAllCompareTreeNodes() {
    let compareTree = getCompareTree();
    if (compareTree != null) {
        compareTree.close_all();
    }
}
