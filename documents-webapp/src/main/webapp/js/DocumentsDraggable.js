/*
 * Copyright (C) 2023 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
(function (exoi18n) {
    'use strict';

    let table = null;
    let breadCrumbList = null;
    let breadcrumbListId = null;
    let parentDragElement = null;
    let currentOpenedFolder = null;
    let openFolderTimer = null;
    let dragElements = [],
        dragTooltipElement = null,
        mouseDrag = false;

    const DocumentsDraggable = function () {/**/
    };
    DocumentsDraggable.prototype.invoke = function (tableId, _breadcrumbListId) {
        table = document.getElementById(tableId);
        breadcrumbListId = _breadcrumbListId
        bindMouse();
    };

    function bindMouse() {
        document.addEventListener('dragstart', handleDragStart);
        document.addEventListener('open-folder-on-hover', handleOpenFolder);
        document.addEventListener('cancel-action-alert', handleCancelAction);
        document.addEventListener("contextmenu", clearElements);
        document.addEventListener('mousemove', moveRowPosition);
        document.addEventListener('mouseup', dropElements);
        document.addEventListener('dragend', dropElements);
        document.addEventListener('documents-folder-opened', handleFolderOpened)
    }

    function handleFolderOpened(event) {
        currentOpenedFolder = event.detail.folder;
        setTimeout(() => {
            checkEnableDropOnBreadCrumb();
        }, 200)
    }

    function handleDragStart(event) {
        if (event.button !== 0) {
            return true;
        }
        const target = getTargetRow(event.target);
        const selections = getSelectedRows();
        parentDragElement = getParentDragElement();
        if (selections.length) {
            addDraggableRows(target, selections);
        } else {
            addDraggableRows(target, [target]);
            target.classList.add('v-data-table__selected', 'drag-source');
        }
        mouseDrag = true;
        checkEnableDropOnBreadCrumb();
    }

    function handleOpenFolder(event) {
        if (!mouseDrag) {
            return;
        }
        clearTimeout(openFolderTimer);
        openFolderTimer = setTimeout(() => {
            if (event.detail.breadcrumb) {
                document.dispatchEvent(new CustomEvent('document-open-root-folder-to-drop'))
            } else {
                document.dispatchEvent(new CustomEvent('document-open-folder-to-drop', event))
            }
        }, 2000);
    }

    function clearElements() {
        dragElements.forEach(element => {
            element.remove();
        });
        dragElements = [];
        dragTooltipElement?.remove();
        mouseDrag = false;
        parentDragElement = null;
        clearTimeout(openFolderTimer);
        table.querySelector('tr.is-dragover')?.classList.remove('is-dragover', 'grey', 'lighten-3');
        table.querySelector('tr.drag-source')?.classList.remove('v-data-table__selected', 'drag-source');
        getBreadCrumbListElement()?.classList.remove('is-intersected', 'is-drop-active');
        getDocumentsBoyElement().classList.remove('is-drop-active');
    }

    function handleCancelAction() {
        table.querySelector('tr.is-intersected')?.classList.remove('is-intersected');
    }

    function getDocumentsBoyElement() {
        return document.querySelectorAll('div.documents-body')[0];
    }

    function checkDropOnDocumentsBody(onDrop) {
        const bodyIntersected = isIntersection(dragTooltipElement, getDocumentsBoyElement()) && !isIntersection(dragTooltipElement, table);
        const allowed = canDropOnListFiles();
        if (bodyIntersected) {
            if (allowed) {
                dragTooltipElement.style.cursor = 'move';
                getDocumentsBoyElement().classList.add('is-drop-active');
                if (onDrop) {
                    document.dispatchEvent(new CustomEvent('move-dropped-documents', {
                        detail: {
                            sourceFiles: getSourceFiles(),
                            currentOpenedFolder: currentOpenedFolder,
                        }
                    }));
                }
            } else {
                dragTooltipElement.style.cursor = 'not-allowed';
            }
        } else {
            getDocumentsBoyElement().classList.remove('is-drop-active');
        }
    }

    function dropElements() {
        if (!mouseDrag) {
            return;
        }
        checkIntersection(true);
        checkDropAndMoveOnBreadcrumb(true)
        checkDropOnDocumentsBody(true);
        clearElements();
    }

    function moveRowPosition(event) {
        if (!mouseDrag) {
            return;
        }
        const coords = getMouseCoords(event);
        dragTooltipElement.style.top = `${(coords.y - 15)}px`;
        dragTooltipElement.style.left = `${(coords.x + -5)}px`;
        clearTimeout(openFolderTimer);
        checkIntersection();
        checkDropAndMoveOnBreadcrumb();
        checkDropOnDocumentsBody();
        scrollOnDrag();
    }

    function scrollOnDrag() {
        const dragElementRect = dragTooltipElement.getBoundingClientRect();
        if (dragElementRect.top < 20) {
            document.getElementById("UISiteBody").scrollBy(0, -10);
        }
        if (dragElementRect.bottom > window.innerHeight) {
            document.getElementById("UISiteBody").scrollBy(0, 10);
        }
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

    function checkDropAndMoveOnBreadcrumb(onDrop) {
        if (isIntersection(dragTooltipElement, getBreadCrumbListElement())) {
            getBreadCrumbListElement().classList.replace('is-intersected', 'is-drop-active');
            document.dispatchEvent(new CustomEvent('open-folder-on-hover', {
                detail: {breadcrumb: true}
            }));
            const allowed = getBreadCrumbListElement().dataset.canedit === 'true';
            dragTooltipElement.style.cursor = allowed ? 'move' : 'not-allowed';
            if (onDrop && allowed) {
                if (getRootFolder().dataset.fileid === parentDragElement.dataset.fileid) {
                    return;
                }
                document.dispatchEvent(new CustomEvent('move-dropped-documents-on-breadcrumb', {
                    detail: {
                        sourceFiles: getSourceFiles(),
                        destinationId: getRootFolder().dataset.fileid
                    }
                }));
            }
        } else {
            getBreadCrumbListElement().classList.replace('is-drop-active', 'is-intersected');
        }
    }

    function getSourceFiles() {
        const sourceFiles = [];
        dragElements.forEach(element => {
            sourceFiles.push({
                id: element.dataset.fileid,
                folder: element.dataset.isfolder === 'true'
            });
        });
        return sourceFiles;
    }

    function isIntersection(element1, element2) {
        const rect1 = element1.getBoundingClientRect();
        const rect2 = element2.getBoundingClientRect();
        return !(
            rect1.top > rect2.bottom ||
            rect1.right < rect2.left ||
            rect1.bottom < rect2.top ||
            rect1.left > rect2.right
        );
    }

    function checkIntersection(onDrop) {
        const dPos = dragTooltipElement.getBoundingClientRect();
        const currStartY = dPos.y;

        const sourceIds = [];
        dragElements.forEach(element => {
            sourceIds.push(element.dataset.fileid);
        });

        const rows = getRows();
        for (const rowElem of rows) {
            let rowSize = rowElem.getBoundingClientRect(),
                rowStartY = rowSize.y;
            rowElem.classList.remove('is-intersected');
            rowElem.classList.remove('is-dragover', 'grey', 'lighten-3');
            if (isIntersection(dragTooltipElement, rowElem)) {
                const destinationId = rowElem.dataset.fileid;
                const isFolder = rowElem.dataset.isfolder === 'true';
                const canEdit = rowElem.dataset.canedit === 'true';
                const allowed = !sourceIds.includes(destinationId) && (isFolder && canEdit) || canDropOnListFiles();
                if (Math.abs(currStartY - rowStartY) < rowSize.height / 2) {
                    rowElem.classList.add('is-dragover', 'grey', 'lighten-3');
                    dragTooltipElement.style.cursor = allowed ? 'move' : 'not-allowed';
                    checkDropElements(allowed, onDrop, rowElem, getSourceFiles())
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
                        currentOpenedFolder: currentOpenedFolder,
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

    function getParentDragElement() {
        return getBreadCrumbListElement().querySelector('div.documents-tree-item:last-child').cloneNode(true);
    }

    function getRootFolder() {
        return getBreadCrumbListElement().querySelector('div.documents-tree-item:first-child').cloneNode(true);
    }

    function isRootFolder() {
        return getBreadCrumbListElement().querySelectorAll('div.documents-tree-item')?.length === 1;
    }

    function canDropOnListFiles() {
        return currentOpenedFolder && currentOpenedFolder?.id !== parentDragElement.dataset.fileid
            && (currentOpenedFolder?.acl?.canEdit || currentOpenedFolder?.accessList?.canEdit);
    }

    function getBreadCrumbListElement() {
        if (!breadCrumbList) {
            breadCrumbList = document.getElementById(breadcrumbListId);
        }
        return breadCrumbList;
    }

    function checkEnableDropOnBreadCrumb() {
        const isRootPath = isRootFolder();
        if (!isRootPath && mouseDrag) {
            getBreadCrumbListElement().classList.add('is-intersected');
        } else {
            getBreadCrumbListElement().classList.remove('is-intersected', 'is-drop-active');
        }
    }

    function addDraggableToolTip(target, selectionsLength) {
        dragTooltipElement = document.createElement('div');
        dragTooltipElement.classList.add('VuetifyApp');
        dragTooltipElement.setAttribute('id', 'documents-drag-tooltip')
        dragTooltipElement.style.position = 'absolute';
        dragTooltipElement.style.background = getStyle(target, 'backgroundColor');
        const newTD = document.createElement('div');
        newTD.classList.add('v-application');
        newTD.innerHTML = `<div class="text-center text-tooltip width-full">
                                      <i class="fa fa-sticky-note white--text"/>
                                      <span class="document-title ms-2 font-weight-regular">
                                          ${exoi18n.i18n.t('document.multiple.drag.action.message', {0: selectionsLength})}
                                      </span>
                                    </div>`;
        newTD.style.width = '180px';
        dragTooltipElement.style.height = '25px';
        newTD.classList.add('pa-1');
        const tPos = target.getBoundingClientRect(),
            dPos = dragTooltipElement.getBoundingClientRect();
        dragTooltipElement.style.bottom = `${(dPos.y - tPos.y) + 2}px`;
        dragTooltipElement.style.left = '-1px';
        dragTooltipElement.appendChild(newTD);
        document.getElementsByTagName('BODY')[0].appendChild(dragTooltipElement);

        document.dispatchEvent(new MouseEvent('mousemove',
            {view: window, cancelable: true, bubbles: true}
        ));

    }

    return new DocumentsDraggable();
})(exoi18n)