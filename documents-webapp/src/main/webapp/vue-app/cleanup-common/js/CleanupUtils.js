/*
 * Copyright (C) 2026 eXo Platform SAS.
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

const BYTE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB'];

export function formatBytes(bytes) {
  if (bytes == null || isNaN(bytes)) {
    return '';
  }
  let value = Number(bytes);
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < BYTE_UNITS.length - 1) {
    value = value / 1024;
    unitIndex++;
  }
  const rounded = value >= 10 || unitIndex === 0 ? Math.round(value) : Math.round(value * 10) / 10;
  return `${rounded} ${BYTE_UNITS[unitIndex]}`;
}

export function formatDate(timeInMillis) {
  if (!timeInMillis) {
    return '';
  }
  const lang = eXo?.env?.portal?.language || 'en';
  return new Date(timeInMillis).toLocaleDateString(lang, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

export function formatDateTime(timeInMillis) {
  if (!timeInMillis) {
    return '';
  }
  const lang = eXo?.env?.portal?.language || 'en';
  return new Date(timeInMillis).toLocaleString(lang, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatRemaining(deadlineMillis, nowMillis) {
  const remainingMillis = (deadlineMillis || 0) - (nowMillis || Date.now());
  if (remainingMillis <= 0) {
    return '';
  }
  const totalMinutes = Math.ceil(remainingMillis / 60000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  if (days) {
    return hours ? `${days}d ${hours}h` : `${days}d`;
  } else if (hours) {
    return minutes ? `${hours}h ${minutes}m` : `${hours}h`;
  } else {
    return `${minutes}m`;
  }
}

export function formatEta(etaSeconds) {
  if (etaSeconds == null || etaSeconds < 0) {
    return '';
  }
  const minutes = Math.floor(etaSeconds / 60);
  const seconds = Math.floor(etaSeconds % 60);
  return minutes ? `${minutes}m ${seconds}s` : `${seconds}s`;
}

export function progressPercentage(processed, total) {
  if (!total) {
    return 0;
  }
  return Math.min(100, Math.round(processed * 100 / total));
}
