export function injectSortTooltip(tooltipName){
  const el = document.getElementsByClassName('last-updated-tooltip-marker').item(0).getElementsByTagName('i').item(0);
  el.title = tooltipName;
}