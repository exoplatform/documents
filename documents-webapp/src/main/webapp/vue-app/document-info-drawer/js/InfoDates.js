/*
 * Copyright (C) 2026 eXo Platform SAS.
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

const TIME_FORMAT = {
  hour: '2-digit',
  minute: '2-digit',
};

const DAY_FORMAT = {
  month: 'short',
  day: 'numeric',
};

const DAY_WITH_YEAR_FORMAT = {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
};

const EXACT_FORMAT = {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
};

/**
 * Reads a date the REST answers as a number, a string or a date.
 *
 * @param {number|string|Date} value the date
 * @returns {Date} the date, or null when there is none
 */
export function toDate(value) {
  const time = value && new Date(value).getTime();
  return Number.isFinite(time) && time && new Date(time) || null;
}

/**
 * Names the day of a date in the viewer's time zone: « Today, 14:27 »,
 * « Yesterday, 14:27 », else the day, with its year when it is not this year.
 *
 * @param {number|string|Date} value the date
 * @param {Object} vm a component, for its $t and $dateUtil
 * @returns {string} the label, or an empty string when there is no date
 */
export function formatDay(value, vm) {
  const date = toDate(value);
  if (!date) {
    return '';
  }
  const lang = eXo.env.portal.language;
  const today = new Date();
  const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
  const time = vm.$dateUtil.formatDateObjectToDisplay(date, TIME_FORMAT, lang);
  if (sameDay(date, today)) {
    return vm.$t('documents.drawer.details.date.today', {0: time});
  } else if (sameDay(date, yesterday)) {
    return vm.$t('documents.drawer.details.date.yesterday', {0: time});
  }
  const format = date.getFullYear() === today.getFullYear() && DAY_FORMAT || DAY_WITH_YEAR_FORMAT;
  return vm.$dateUtil.formatDateObjectToDisplay(date, format, lang);
}

/**
 * Formats a date in full, for the hover of a short one.
 *
 * @param {number|string|Date} value the date
 * @param {Object} vm a component, for its $dateUtil
 * @returns {string} the date and time, or an empty string
 */
export function formatExact(value, vm) {
  const date = toDate(value);
  return date && vm.$dateUtil.formatDateObjectToDisplay(date, EXACT_FORMAT, eXo.env.portal.language) || '';
}

/**
 * Tells whether two dates fall on the same calendar day.
 *
 * @param {Date} a a date
 * @param {Date} b another date
 * @returns {boolean} true when same year, month and day
 */
function sameDay(a, b) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}
