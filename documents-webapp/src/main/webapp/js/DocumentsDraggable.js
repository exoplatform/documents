(function (exoi18n) {
    'use strict';

    let table = null;
    let openFolderTimer = null;
    let currentRow = null,
        realDragElement = null,
        dragElements = [],
        dragTooltipElement = null,
        mouseDrag = false;

    const DocumentsDraggable = function () {/**/
    };
    DocumentsDraggable.prototype.invoke = function (tableId) {
        table = document.getElementById(tableId);
        bindMouse();
    };

    function bindMouse() {
        table.addEventListener('dragstart', handleDragStart);
        document.addEventListener('open-folder-on-hover', handleOpenFolder);
        document.addEventListener('cancel-action-alert', handleCancelAction);
        document.addEventListener("contextmenu", clearElements);
        document.addEventListener('mousemove', moveRowPosition);
        document.addEventListener('mouseup', dropElements);
        document.addEventListener('dragend', dropElements);
    }

    function handleDragStart(event) {
        if (event.button !== 0) {
            return true;
        }
        const target = getTargetRow(event.target);
        const selections = getSelectedRows();
        realDragElement = target.cloneNode(true);
        currentRow = target;
        currentRow.style.cursor = 'not-allowed'
        if (selections.length) {
            addDraggableRows(target, selections);
        } else {
            addDraggableRows(target, [target]);
            target.classList.add('v-data-table__selected', 'drag-source');
        }
        mouseDrag = true;
    }

    function handleOpenFolder(event) {
        if (!mouseDrag) {
            return;
        }
        clearTimeout(openFolderTimer);
        openFolderTimer = setTimeout(() => {
            document.dispatchEvent(new CustomEvent('document-open-folder-to-drop', event))
        }, 2000);
    }

    function clearElements() {
        dragElements.forEach(element => {
            element.remove();
        });
        dragElements = [];
        dragTooltipElement.remove();
        realDragElement = null;
        mouseDrag = false;
        clearTimeout(openFolderTimer);
        table.querySelector('tr.is-dragover')?.classList.remove('is-dragover', 'grey', 'lighten-3');
        table.querySelector('tr.drag-source')?.classList.remove('v-data-table__selected', 'drag-source');
    }

    function handleCancelAction() {
        table.querySelector('tr.is-intersected')?.classList.remove('is-intersected');
    }

    function dropElements() {
        if (!mouseDrag) {
            return;
        }
        checkIntersection(true);
        clearElements();
    }

    function moveRowPosition(event) {
        if (!mouseDrag) {
            return;
        }
        const coords = getMouseCoords(event);
        const scroll = document.getElementById('UISiteBody').scrollTop;
        dragTooltipElement.style.top = (coords.y + scroll - 72) + 'px';
        dragTooltipElement.style.left = (coords.x) + 'px';
        clearTimeout(openFolderTimer);
        checkIntersection();
    }

    function getSelectedRows() {
        return document.querySelectorAll("tr.v-data-table__selected");
    }

    function getTargetRow(target) {
        const elemName = target.tagName.toLowerCase();
        if (elemName === 'tr') {
            return target;
        } else {
            return target.closest('tr');
        }
    }

    function getMouseCoords(event) {
        return {
            x: event.clientX,
            y: event.clientY
        };
    }

    function getStyle(target, styleName) {
        const computedStyle = getComputedStyle(target);
        const style = computedStyle[styleName];

        return style ? style : null;
    }

    function isIntersecting(min0, max0, min1, max1) {
        return Math.max(min0, max0) >= Math.min(min1, max1) &&
            Math.min(min0, max0) <= Math.max(min1, max1);
    }

    function checkIntersection(onDrop) {
        let dPos = realDragElement.getBoundingClientRect();
        const index = dragElements.findIndex(element => element.dataset.fileid === realDragElement.dataset.fileid);
        if (index !== -1) {
            dPos = dragTooltipElement.getBoundingClientRect();
        }
        const currStartY = dPos.y;
        const currEndY = currStartY + dPos.height;

        const sourceIds = [];
        const sourceFiles = [];
        dragElements.forEach(element => {
            sourceIds.push(element.dataset.fileid);
            sourceFiles.push({
                id: element.dataset.fileid,
                folder: element.dataset.isfolder === 'true'
            });
        });

        const rows = getRows();
        for (const rowElem of rows) {
            let rowSize = rowElem.getBoundingClientRect(),
                rowStartY = rowSize.y, rowEndY = rowStartY + rowSize.height;
            rowElem.classList.remove('is-intersected');
            rowElem.classList.remove('is-dragover', 'grey', 'lighten-3');
            if (isIntersecting(currStartY, currEndY, rowStartY, rowEndY)) {
                const destinationId = rowElem.dataset.fileid;
                const isFolder = rowElem.dataset.isfolder === 'true';
                const canEdit = rowElem.dataset.canedit === 'true';
                const allowed = !sourceIds.includes(destinationId) && isFolder && canEdit;
                if (Math.abs(currStartY - rowStartY) < rowSize.height / 2) {
                    rowElem.classList.add('is-dragover', 'grey', 'lighten-3');
                    dragTooltipElement.style.cursor = allowed ? 'move' : 'not-allowed';
                    checkDropElements(allowed, onDrop, rowElem, sourceFiles)
                }
            }
        }
    }

    function checkDropElements(allowed, onDrop, rowElem, sourceFiles) {
        if (allowed) {
            rowElem.classList.add('is-intersected');
            document.dispatchEvent(new CustomEvent('open-folder-on-hover', {
                detail: {
                    folder: rowElem.dataset.fileid,
                }
            }));
            if (onDrop) {
                document.dispatchEvent(new CustomEvent('move-dropped-documents', {
                    detail: {
                        sourceFiles: sourceFiles,
                        destinationId: rowElem.dataset.fileid
                    }
                }));
            }
        }
    }

    function addDraggableRows(target, selections) {
        addDraggableToolTip(target, selections.length);
        selections.forEach(selection => {
            dragElements.push(selection.cloneNode(true));
        });
    }

    function getRows() {
        return table.querySelectorAll('tbody tr');
    }

    function addDraggableToolTip(target, selectionsLength) {
        dragTooltipElement = document.createElement('tr');
        dragTooltipElement.style.position = 'absolute';
        dragTooltipElement.style.background = getStyle(target, 'backgroundColor');
        const newTD = dragTooltipElement.insertCell(0);
        newTD.innerHTML = `<div class="text-center">
                                      <i class="fa fa-sticky-note white--text"/>
                                      <span class="document-title ms-2 font-weight-regular">
                                          ${exoi18n.i18n.t('document.multiple.drag.action.message', {0: selectionsLength})}
                                      </span>
                                    </div>`;
        newTD.style.width = '180px';
        dragTooltipElement.style.height = '30px';
        newTD.style.height = '30px';
        newTD.classList.add('pa-1', 'drag-tooltip');
        const tPos = target.getBoundingClientRect(),
            dPos = dragTooltipElement.getBoundingClientRect();
        dragTooltipElement.style.bottom = `${(dPos.y - tPos.y) + 2}px`;
        dragTooltipElement.style.left = '-1px';

        document.querySelector('div#DocumentsApplication').appendChild(dragTooltipElement);

        document.dispatchEvent(new MouseEvent('mousemove',
            {view: window, cancelable: true, bubbles: true}
        ));

    }

    return new DocumentsDraggable();
})(exoi18n)