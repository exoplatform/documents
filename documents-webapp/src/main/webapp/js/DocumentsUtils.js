/*
 * Copyright (C) 2024 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

export function injectSortTooltip(tooltipName,markerClass){
  document.getElementsByClassName(markerClass).forEach(element => {
    element.getElementsByTagName('i').item(0);
    element.title = tooltipName; 
  });
}

export function getSize(size) {
  if ( size === 0){
    return  {value: 0, unit: 'B'};
  } 
  const m = size > 0 ? 1 : -1;
  const k = Math.floor((Math.log2(Math.abs(size)) / 10));
  let rank = `B`;
  if (k!==0) {
    rank = `${'KMGT'[k - 1]}B`;
  }    
  const count = (Math.abs(size) / Math.pow(1024, k)).toFixed(2);
  return {value: Math.round(count*m), unit: rank};
}