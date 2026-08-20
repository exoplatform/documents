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
 *
 * Scoped to the USER's keep/un-keep decisions, and to them only. The purge's own
 * failure codes (cleanup.referentialIntegrity, cleanup.deleteError...) are a
 * DIFFERENT notion and must NOT be added here: their retryability is decided by
 * the server, which answers it per group on the campaign failures endpoint, and
 * the admin UI reads that verdict instead of re-deriving one from the code.
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
/**
 * Splits a duration into the units a human reads, most significant first, and
 * keeps at most the TWO most significant non-zero ones: '2d 4h', '2h 46m',
 * '46m 41s', '41s'. Seconds only ever show up when the whole duration is under
 * an hour — nobody reads '2d 4h 17m 3s'.
 *
 * Pure on purpose: the unit LABELS are localized by $cleanupDuration (see
 * ../services.js), which is the only place with a $t to call.
 *
 * @param {Number} millis duration in milliseconds
 * @returns {Array} [{unit, value}] with unit in 'day'|'hour'|'minute'|'second'
 */
/**
 * State -> Vuetify colour, defined ONCE for the three tables and the campaign
 * header that render these chips: a per-template ternary would drift the day a
 * state is added, and the same state must not read differently depending on
 * where it is shown.
 *
 * The scale carries meaning rather than decoration: what still needs a decision
 * is amber, what refused needs attention and is red, what is settled fades to
 * grey, and what is safe is green.
 */
const ITEM_STATE_COLORS = {
  CANDIDATE: 'warning',              // slated for deletion: the rows to look at
  EXEMPTED: 'success',               // kept by its owner
  SPARED_BY_MODIFICATION: 'info',    // spared, but by a side effect, not a decision
  GONE: 'grey lighten-1',            // vanished on its own, nothing was done
  PURGED: 'grey',                    // done, unremarkable
  SKIPPED: 'error',                  // something refused: needs attention
};

const CAMPAIGN_STATE_COLORS = {
  DRAFT: 'grey',
  DRY_RUN_RUNNING: 'info',           // working, and harmless
  SIMULATED: 'indigo',               // a decision point: publish or not
  PUBLISHED: 'warning',              // the users' clock is running
  LOCKED: 'deep-orange',             // review closed, purge imminent
  EXECUTING: 'error',                // deleting right now
  COMPLETED: 'success',
  CANCELLED: 'grey darken-1',
};

/**
 * The two states worth shouting: a purge in flight and a refusal. They render
 * as filled chips, everything else stays outlined so a table of thirty rows
 * does not turn into thirty coloured blocks.
 */
const LOUD_STATES = ['EXECUTING', 'SKIPPED'];

/**
 * @param {String} state item state
 * @returns {String} Vuetify colour for that state, grey when unknown
 */
export function itemStateColor(state) {
  return ITEM_STATE_COLORS[state] || 'grey';
}

/**
 * @param {String} state campaign state
 * @returns {String} Vuetify colour for that state, grey when unknown
 */
export function campaignStateColor(state) {
  return CAMPAIGN_STATE_COLORS[state] || 'grey';
}

/**
 * @param {String} state item or campaign state
 * @returns {Boolean} true when the chip must be filled rather than outlined
 */
export function isLoudState(state) {
  return LOUD_STATES.includes(state);
}

export function durationParts(millis) {
  if (!millis || millis <= 0) {
    return [];
  }
  const totalSeconds = Math.floor(millis / 1000);
  const parts = [
    {unit: 'day', value: Math.floor(totalSeconds / 86400)},
    {unit: 'hour', value: Math.floor((totalSeconds % 86400) / 3600)},
    {unit: 'minute', value: Math.floor((totalSeconds % 3600) / 60)},
    {unit: 'second', value: totalSeconds % 60},
  ];
  const firstIndex = parts.findIndex(part => part.value > 0);
  if (firstIndex < 0) {
    // Under a second, but not zero: never render an empty duration
    return [{unit: 'second', value: 0}];
  }
  return parts.slice(firstIndex, firstIndex + 2).filter(part => part.value > 0);
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
