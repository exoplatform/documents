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

/**
 * Intl options of a date + time rendering, handed to the platform's shared
 * <date-format> component: dates are NEVER formatted here, and byte sizes go
 * through $cleanupSize (see cleanup-common/services.js). What is left below is
 * genuinely local to a cleanup campaign: the grace-period countdown, the scan
 * ETA and the progress percentage.
 */
export const DATE_TIME_FORMAT = {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
};

/**
 * The ONLY per-item failure reason a retry can fix: every other code the bulk
 * keep/un-keep endpoints report is a permanent refusal (not the owner, review
 * closed, item not decidable anymore...), so telling the user to try again would
 * send them in circles.
 */
export const RETRYABLE_FAILURE_REASONS = ['cleanup.keepFailed', 'cleanup.unkeepFailed'];

/**
 * Renders a REMAINING DURATION — never a deadline compared to the browser clock.
 * The server ships the remaining milliseconds (campaign/summary DTO
 * 'remainingMillis'), the caller counts them down locally and hands the result
 * here, so a skewed client can no longer disagree with the server about whether
 * a window is still open.
 *
 * @param {number} remainingMillis milliseconds left, 0 or negative for none
 * @returns {string} human-readable duration, empty when nothing is left
 */
export function formatDuration(remainingMillis) {
  if (!remainingMillis || remainingMillis <= 0) {
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

/**
 * Folds the per-item failures of a bulk keep/un-keep outcome into an ordered
 * [{reason, count}] list. The backend reports a localizable MESSAGE CODE per
 * item (cleanup.notOwner, cleanup.reviewClosed, cleanup.keepFailed...); grouping
 * them is what lets the UI tell the user WHY, instead of a bare count.
 *
 * @param {Array} failures per-item failures, each carrying a 'reason' code
 * @returns {Array} [{reason, count}], one entry per distinct reason
 */
export function groupFailuresByReason(failures) {
  const counts = new Map();
  (failures || []).forEach(failure => {
    const reason = failure?.reason || '';
    counts.set(reason, (counts.get(reason) || 0) + 1);
  });
  return Array.from(counts.entries()).map(([reason, count]) => ({reason, count}));
}

/**
 * True when EVERY reported failure is transient, i.e. when suggesting a retry is
 * actually useful. An empty list is not retryable: there is nothing to retry.
 *
 * @param {Array} failures per-item failures, each carrying a 'reason' code
 * @returns {boolean} true when suggesting a retry makes sense
 */
export function isRetryable(failures) {
  return !!failures?.length && failures.every(failure => RETRYABLE_FAILURE_REASONS.includes(failure?.reason));
}
