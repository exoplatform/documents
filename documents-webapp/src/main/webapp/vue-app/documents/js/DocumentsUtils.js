export function injectSortTooltip(tooltipName,markerClass){
  document.getElementsByClassName(markerClass).forEach(element => {
    element.getElementsByTagName('i').item(0);
    element.title = tooltipName; 
  });
}