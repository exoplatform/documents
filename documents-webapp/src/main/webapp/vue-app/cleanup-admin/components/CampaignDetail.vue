<!--
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
 *
-->
<template>
  <div v-if="campaign">
    <div class="d-flex align-center flex-wrap">
      <h5 class="my-0 text-color font-weight-bold">{{ campaign.name }}</h5>
      <!-- ONE edit path, the drawer — the name used to be renamed inline right
           here as well, which meant two validations and two error surfaces for
           the same attribute: exactly the divergence just fixed server-side,
           where creation and update now share one validation. The name is pure
           metadata (no state transition, nothing keys off it) so it stays
           editable in ANY state, terminal ones included — relabelling an
           already-completed report is a legitimate need; the grace period is
           the field the drawer gates by state. The drawer itself is hosted ONCE
           by CleanupAdmin (see the 'edit' event below), never a second copy -->
      <v-tooltip bottom>
        <template #activator="{on, attrs}">
          <div
            v-bind="attrs"
            class="ms-1"
            v-on="on">
            <v-btn
              :aria-label="$t('cleanup.admin.campaign.edit')"
              icon
              small
              @click="edit">
              <v-icon size="14">fas fa-pencil-alt</v-icon>
            </v-btn>
          </div>
        </template>
        <span>{{ $t('cleanup.admin.campaign.editTooltip') }}</span>
      </v-tooltip>
      <v-chip
        :color="$cleanupUtils.campaignStateColor(campaign.state)"
        :outlined="!$cleanupUtils.isLoudState(campaign.state)"
        :dark="$cleanupUtils.isLoudState(campaign.state)"
        class="ms-3"
        small>
        {{ $t(`cleanup.campaign.state.${campaign.state}`) }}
      </v-chip>
      <v-spacer />
      <v-btn
        v-if="campaign.state === 'SIMULATED'"
        :loading="actionInProgress"
        class="btn btn-primary me-2"
        @click="publish">
        {{ $t('cleanup.admin.campaign.publish') }}
      </v-btn>
      <v-tooltip
        v-if="executeOffered"
        :disabled="executeEnabled"
        bottom>
        <template #activator="{ on, attrs }">
          <div v-bind="attrs" v-on="on">
            <v-btn
              :disabled="!executeEnabled"
              :loading="actionInProgress"
              class="btn btn-primary me-2"
              @click="execute">
              {{ $t('cleanup.admin.campaign.execute') }}
            </v-btn>
          </div>
        </template>
        <span>{{ $t('cleanup.admin.execute.remaining', {0: remainingTime}) }}</span>
      </v-tooltip>
      <v-btn
        v-if="cancelable"
        :loading="actionInProgress"
        class="btn me-2"
        @click="cancel">
        {{ $t('cleanup.admin.campaign.cancel') }}
      </v-btn>
      <v-btn
        v-if="campaign.archiveAvailable"
        :href="$cleanupService.getArchiveUrl(campaign.id)"
        class="btn"
        target="_blank">
        {{ $t('cleanup.admin.campaign.downloadArchive') }}
      </v-btn>
    </div>
    <div class="text-light-color caption mt-2">
      {{ paramsSummary }}
    </div>
    <!-- Each milestone date goes through the platform's shared <date-format>
         rather than a locally formatted string, so the row is built in the
         template instead of a joined computed -->
    <div class="text-light-color caption d-flex flex-wrap align-center">
      <div
        v-for="(field, index) in dateFields"
        :key="field"
        class="d-flex align-center">
        <span v-if="index" class="mx-1">|</span>
        <span>{{ $t(`cleanup.admin.campaign.${field}`) }}:</span>
        <date-format
          v-if="campaign[field]"
          :value="campaign[field]"
          :format="$cleanupUtils.DATE_TIME_FORMAT"
          class="ms-1" />
        <span v-else class="ms-1">-</span>
      </div>
    </div>
    <!-- FILES the scan could not judge, at the top and not buried in a table:
         each one was a WARN in the server log and nothing else, while the campaign
         reported a complete run at 100%. Shown DURING the run too, so the problem
         is visible while it is still worth acting on -->
    <v-alert
      v-if="skippedNodeCount"
      type="warning"
      dense
      text
      class="mt-4 mb-0">
      <div class="d-flex align-center flex-wrap">
        <span>{{ $t('cleanup.admin.campaign.skippedNodes', {0: skippedNodeCount}) }}</span>
        <v-spacer />
        <v-btn
          small
          text
          class="ms-2"
          @click="$refs.scanFailuresDrawer.open()">
          {{ $t('cleanup.admin.campaign.skippedDetails') }}
        </v-btn>
      </div>
    </v-alert>
    <document-cleanup-campaign-scan-failures-drawer ref="scanFailuresDrawer" :scan-units="scanUnits" />
    <document-cleanup-campaign-scan-units-drawer ref="scanUnitsDrawer" :scan-units="scanUnits" />
    <document-cleanup-campaign-stats
      :campaign="campaign"
      :scan-units="scanUnits"
      @open-subtrees="$refs.scanUnitsDrawer.open()" />
    <!-- COMPLETENESS of the report, ABOVE the report itself and above the skip
         reasons below: a dry run that could not walk a subtree produces a report
         missing it entirely, and this used to read as a clean 100% run. Shown from
         SIMULATED on — the state the admin publishes FROM, so the warning arrives
         before the decision, not after it. No retry is offered: these subtrees
         already spent every walk attempt they had (the watchdog re-walked them
         while any remained), so a button here would only fail identically -->
    <div
      v-if="scanFailures.length"
      role="alert"
      class="d-flex flex-column mt-4">
      <div class="d-flex align-center flex-wrap">
        <v-icon size="16" class="warning--text me-2">fas fa-exclamation-triangle</v-icon>
        <number-format :value="totalScanFailures" class="text-color font-weight-bold" />
        <span class="ms-1 text-color">{{ $t('cleanup.admin.scanFailures.headline') }}</span>
      </div>
      <span class="text-light-color caption">{{ $t('cleanup.admin.scanFailures.warning') }}</span>
      <div
        v-for="group in scanFailures"
        :key="group.reason"
        class="d-flex align-center mt-1">
        <v-chip
          color="warning"
          class="me-2"
          outlined
          x-small>
          {{ $t('cleanup.admin.scanFailures.notWalked') }}
        </v-chip>
        <number-format :value="group.count" class="text-color font-weight-bold" />
        <span class="ms-2 text-light-color caption">{{ failureLabel(group.reason) }}</span>
      </div>
    </div>
    <!-- The grouped skip reasons of a FINISHED run, and the only place a retry
         is offered from. Nothing is shown when the endpoint answers an empty
         list: no failure, or item rows the retention job already purged -->
    <div v-if="failures.length" class="mt-4">
      <div class="d-flex align-center flex-wrap">
        <number-format :value="totalFailures" class="text-color font-weight-bold" />
        <span class="ms-1 text-color">{{ $t('cleanup.admin.failures.headline', {0: failures.length}) }}</span>
        <v-spacer />
        <!-- Kept VISIBLE but disabled when nothing is retryable, rather than
             vanishing silently: the tooltip is what tells the admin the
             remaining failures are deterministic and would fail identically.
             Wrapped in a div because a disabled v-btn emits no mouse event, so
             it would never open its own tooltip (same trick as Execute above) -->
        <v-tooltip bottom>
          <template #activator="{ on, attrs }">
            <div v-bind="attrs" v-on="on">
              <v-btn
                :aria-label="$t('cleanup.admin.failures.retry')"
                :disabled="!retryEnabled"
                :loading="actionInProgress"
                class="btn btn-primary"
                @click="retry">
                {{ $t('cleanup.admin.failures.retry') }}
              </v-btn>
            </div>
          </template>
          <span>{{ retryTooltip }}</span>
        </v-tooltip>
      </div>
      <!-- Retryability is the SERVER's verdict, carried by each group and never
           re-derived from the reason code here: the execution codes and the
           review-side RETRYABLE_FAILURE_REASONS of CleanupUtils are two
           different notions (see retry() below) -->
      <div
        v-for="group in failures"
        :key="group.reason"
        class="d-flex align-center mt-1">
        <v-chip
          :color="group.retryable ? 'warning' : 'grey'"
          class="me-2"
          outlined
          x-small>
          {{ $t(group.retryable ? 'cleanup.admin.failures.retryable' : 'cleanup.admin.failures.permanent') }}
        </v-chip>
        <number-format :value="group.count" class="text-color font-weight-bold" />
        <span class="ms-2 text-light-color caption">{{ failureLabel(group.reason) }}</span>
      </div>
    </div>
    <document-cleanup-campaign-compare-view
      :campaign="campaign"
      :campaigns="campaigns"
      class="mt-4" />
    <document-cleanup-campaign-items-table
      ref="itemsTable"
      :campaign-id="campaign.id"
      :campaign-state="campaign.state"
      class="mt-4" />
  </div>
</template>
<script>
// Slow safety net around the CometD follow-up, which stays the primary refresh
// path: it also re-evaluates 'now' so the Execute gate and the remaining-time
// tooltip keep ticking instead of freezing on the value captured at load.
const REFRESH_PERIOD_MS = 30000;
// Live refresh on CometD progress events, throttled: a scan pushes one event
// per batch, so an unthrottled refresh would be thousands of reloads
const REFRESH_THROTTLE_MS = 5000;
const DATE_FIELDS = ['startedDate', 'publishedDate', 'lockDate', 'completedDate'];
// Generic sentence shown when the code an endpoint answered carries no bundle
// entry of its own: never leak a raw code (or a raw Spring body) in a toast
const UNEXPECTED_ERROR_KEY = 'cleanup.admin.campaign.unexpectedError';

export default {
  props: {
    campaignId: {
      type: Number,
      default: null,
    },
    campaigns: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      campaign: null,
      actionInProgress: false,
      // Grace time left as computed BY THE SERVER at the last load, plus the local
      // instant it was received at. Only the DIFFERENCE of two local clock reads
      // is ever used (see 'remaining'), never a comparison against a server
      // epoch: a client running a few minutes ahead used to enable Execute early
      // and get a 400 'cleanup.graceNotElapsed' back
      remainingMillis: 0,
      syncedAt: Date.now(),
      now: Date.now(),
      refreshTimerId: null,
      // Monotonic token of the last campaign load STARTED: the fallback tick, the
      // CometD state-change handler, the campaignId watcher and every action
      // refresh all load, so several can be in flight at once and a slow one must
      // not overwrite a newer campaign (see loadCampaign)
      loadToken: 0,
      lastEventRefresh: 0,
      // Per-unit breakdown of a RUNNING dry run, refreshed with the campaign on
      // every load: it is what tells a scan resuming a subtree from one held open
      // by it, which the node percentage cannot. Nulled as soon as the campaign
      // leaves DRY_RUN_RUNNING so a finished campaign shows no stale in-flight row
      scanUnits: null,
      // Execution failures grouped BY THE SERVER ([{reason, count, retryable}]),
      // plus the id of the campaign they were requested for: applyCampaign runs
      // on every load, and this must stay ONE request per completion
      failures: [],
      failuresLoadedFor: null,
      // SCAN failures grouped BY THE SERVER, same [{reason, count, retryable}]
      // shape, tracked separately: they are the verdict of the DRY RUN and stay
      // true for every state that follows it, where the execution failures above
      // only exist once a purge ran
      scanFailures: [],
      scanFailuresLoadedFor: null,
    };
  },
  computed: {
    // From the per-unit breakdown, which is loaded for EVERY state and not only
    // while the scan runs: the unit rows outlive the run, so the number stays
    // readable on the simulation an administrator is about to publish
    skippedNodeCount() {
      return this.scanUnits?.skippedNodeCount || 0;
    },
    dateFields() {
      return DATE_FIELDS;
    },
    cancelable() {
      return this.campaign && !['COMPLETED', 'CANCELLED'].includes(this.campaign.state);
    },
    // Whether the Execute button is SHOWN at all (a state matter), as opposed to
    // executeEnabled, which is whether the server would accept it right now
    executeOffered() {
      return ['PUBLISHED', 'LOCKED'].includes(this.campaign?.state);
    },
    running() {
      return ['DRY_RUN_RUNNING', 'EXECUTING'].includes(this.campaign?.state);
    },
    remaining() {
      return Math.max(0, this.remainingMillis - (this.now - this.syncedAt));
    },
    // A campaign cancelled mid-grace keeps its future lockDate: without the
    // terminal-state guard the grace would stay 'pending' and the fallback timer
    // would keep ticking for weeks on a campaign nothing can happen to anymore
    gracePending() {
      return !['COMPLETED', 'CANCELLED'].includes(this.campaign?.state) && this.remaining > 0;
    },
    // Ticking is only needed while progress can move or a deadline can elapse
    refreshNeeded() {
      return this.running || this.gracePending;
    },
    // The server's own verdict, then the locally counted-down remainder so the
    // button unlocks without waiting for the next refresh — no clock comparison
    executeEnabled() {
      return !!this.campaign?.executable || (this.executeOffered && this.remaining <= 0);
    },
    remainingTime() {
      return this.$cleanupDuration(this.remaining);
    },
    totalFailures() {
      return this.failures.reduce((total, group) => total + (group.count || 0), 0);
    },
    totalScanFailures() {
      return this.scanFailures.reduce((total, group) => total + (group.count || 0), 0);
    },
    // Every state a dry run has ALREADY produced its report in. DRAFT and
    // DRY_RUN_RUNNING are excluded because there is no verdict yet, CANCELLED
    // because nothing will be published from it
    scanReported() {
      return ['SIMULATED', 'PUBLISHED', 'LOCKED', 'EXECUTING', 'COMPLETED'].includes(this.campaign?.state);
    },
    // ONE retryable group is enough: the server requeues what it judges
    // retryable and leaves the rest alone
    retryEnabled() {
      return this.failures.some(group => group.retryable);
    },
    retryTooltip() {
      return this.$t(this.retryEnabled ? 'cleanup.admin.failures.retryTooltip' : 'cleanup.admin.failures.retryDisabled');
    },
    paramsSummary() {
      return this.$t('cleanup.admin.campaign.paramsSummary', {
        0: this.campaign.periodMonths,
        1: this.$cleanupSize(this.campaign.minFileSizeBytes),
        2: this.campaign.graceDays,
        3: this.campaign.maxVersionsPerFile,
        4: this.campaign.excludedPaths?.length || 0,
      });
    },
  },
  watch: {
    campaignId() {
      this.loadCampaign();
    },
    refreshNeeded() {
      this.refreshTimer();
    },
  },
  created() {
    this.loadCampaign();
    document.addEventListener('campaign.progress', this.applyProgressEvent);
    document.addEventListener('campaign.stateChanged', this.applyStateChangedEvent);
    document.addEventListener('visibilitychange', this.onVisibilityChange);
  },
  beforeDestroy() {
    this.stopTimer();
    document.removeEventListener('campaign.progress', this.applyProgressEvent);
    document.removeEventListener('campaign.stateChanged', this.applyStateChangedEvent);
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
  },
  methods: {
    // Loaded on EVERY campaign load, whatever the state: the breakdown is what
    // reports the nodes a settled scan could not evaluate, so a SIMULATED
    // campaign needs it as much as a running one. Guarded by the SAME load token as
    // the campaign it belongs to, so a slow answer cannot repaint a newer
    // campaign's panel; a failure leaves the previous value rather than blanking
    // the panel, since the bar's 100% claim depends on it
    loadScanUnits(campaign) {
      if (!campaign) {
        this.scanUnits = null;
        return;
      }
      const token = this.loadToken;
      this.$cleanupService.getCampaignScanUnits(campaign.id)
        .then(scanUnits => {
          if (token === this.loadToken) {
            this.scanUnits = scanUnits;
          }
        })
        .catch(() => {/* diagnostic only: the panel keeps what it had */});
    },
    loadCampaign() {
      this.loadToken = this.loadToken + 1;
      const token = this.loadToken;
      return this.$cleanupService.getCampaign(this.campaignId)
        .then(campaign => {
          if (token === this.loadToken) {
            this.applyCampaign(campaign);
          }
        })
        .catch(() => {
          if (token === this.loadToken) {
            this.displayAlert(this.$t('cleanup.admin.campaigns.loadError'), 'error');
          }
        })
        .finally(() => {
          if (token === this.loadToken) {
            this.refreshTimer();
          }
        });
    },
    // Re-syncs the countdown on EVERY load, so the local drift accumulated since
    // the previous one is discarded instead of compounding
    applyCampaign(campaign) {
      this.campaign = campaign;
      this.loadScanUnits(campaign);
      this.remainingMillis = campaign?.remainingMillis || 0;
      this.syncedAt = Date.now();
      this.now = this.syncedAt;
      this.syncFailures();
      this.syncScanFailures();
    },
    // The grouped failures are the report of a run that ENDED: they are only
    // asked for in COMPLETED — the sole state a purge has actually run in, so a
    // campaign that never executed is never queried — and they are dropped as
    // soon as the campaign leaves it (a retry sends it back to EXECUTING),
    // instead of leaving the previous attempt's counts on screen. Coming back to
    // COMPLETED therefore reloads them fresh.
    syncFailures() {
      const campaignId = this.campaign?.id;
      if (!campaignId || this.campaign.state !== 'COMPLETED') {
        this.failures = [];
        this.failuresLoadedFor = null;
        return;
      }
      if (this.failuresLoadedFor === campaignId) {
        return;
      }
      // Marked BEFORE the request, a failed one included: applyCampaign is
      // called by the fallback tick, the CometD refresh and every action
      // follow-up, and none of them must turn into a second query
      this.failuresLoadedFor = campaignId;
      this.failures = [];
      return this.$cleanupService.getCampaignFailures(campaignId)
        .then(failures => {
          // Dropped when the admin already switched campaign: same supersession
          // discipline as loadCampaign
          if (this.failuresLoadedFor === campaignId) {
            this.failures = failures || [];
          }
        })
        .catch(() => this.displayAlert(this.$t('cleanup.admin.failures.loadError'), 'error'));
    },
    // The scan verdict is asked for ONCE per campaign, in every state the dry run
    // already reported in — and re-asked when the admin comes back to the
    // campaign, exactly like syncFailures. It is NOT dropped when the state moves
    // on (SIMULATED to PUBLISHED to COMPLETED): the report stays the one that
    // incomplete scan produced, whatever happens to it afterwards
    syncScanFailures() {
      const campaignId = this.campaign?.id;
      if (!campaignId || !this.scanReported) {
        this.scanFailures = [];
        this.scanFailuresLoadedFor = null;
        return;
      }
      if (this.scanFailuresLoadedFor === campaignId) {
        return;
      }
      // Marked BEFORE the request, a failed one included: the same refresh paths
      // that call applyCampaign must not turn into a second query
      this.scanFailuresLoadedFor = campaignId;
      this.scanFailures = [];
      return this.$cleanupService.getCampaignScanFailures(campaignId)
        .then(scanFailures => {
          // Dropped when the admin already switched campaign: same supersession
          // discipline as loadCampaign
          if (this.scanFailuresLoadedFor === campaignId) {
            this.scanFailures = scanFailures || [];
          }
        })
        .catch(() => this.displayAlert(this.$t('cleanup.admin.scanFailures.loadError'), 'error'));
    },
    // Localized through the SHARED $cleanupErrorLabel, like every other cleanup
    // message code: a reason with no bundle entry falls back to the generic
    // sentence rather than being shown raw
    failureLabel(reason) {
      return this.$cleanupErrorLabel(reason, UNEXPECTED_ERROR_KEY);
    },
    // Coming back to the tab must tick IMMEDIATELY, not only re-arm the timer:
    // no refresh happened while the tab was hidden, so the countdown — and the
    // Execute gate it drives — would stay stale for a whole period otherwise
    onVisibilityChange() {
      if (!document.hidden) {
        this.tick();
      }
      this.refreshTimer();
    },
    // Started only while it can change something, and never while the tab is
    // hidden: no polling behind the user's back
    refreshTimer() {
      this.stopTimer();
      if (this.refreshNeeded && !document.hidden) {
        this.refreshTimerId = window.setInterval(this.tick, REFRESH_PERIOD_MS);
      }
    },
    stopTimer() {
      if (this.refreshTimerId) {
        window.clearInterval(this.refreshTimerId);
        this.refreshTimerId = null;
      }
    },
    tick() {
      this.now = Date.now();
      if (this.running) {
        // CometD remains the primary refresh path: this poll only prevents a
        // permanently stale progress bar if the socket never connected or dropped
        this.loadCampaign();
      }
    },
    applyProgressEvent(event) {
      const message = event?.detail;
      if (message && this.campaign && message.campaignId === this.campaign.id) {
        Object.assign(this.campaign, {
          state: message.state,
          processedCount: message.processed,
          totalCount: message.total,
          etaSeconds: message.etaSeconds,
        });
        this.refreshWhatTheEventDoesNotCarry();
      }
    },
    // A progress event carries the counters only, so the candidate count, the
    // reclaimable bytes and the report rows would stay frozen at their
    // page-load values for the whole run. Refreshing them on every event would
    // mean one reload per batch (thousands over a large campaign), so the
    // refresh is THROTTLED: at most one every REFRESH_THROTTLE_MS, which is
    // what makes the report read as live without hammering the endpoints.
    refreshWhatTheEventDoesNotCarry() {
      const now = Date.now();
      if (now - this.lastEventRefresh < REFRESH_THROTTLE_MS) {
        return;
      }
      this.lastEventRefresh = now;
      this.loadCampaign();
      this.$refs.itemsTable?.refreshCurrentPage();
    },
    applyStateChangedEvent(event) {
      if (event?.detail?.campaignId === this.campaignId) {
        this.loadCampaign().then(() => this.$emit('refresh'));
      }
    },
    // Asks CleanupAdmin, which hosts the ONE create/edit drawer instance, to
    // open it on this campaign. The updated campaign it answers comes back
    // through applyCampaign, so the header, the state chip and the grace
    // countdown all re-sync from the response — the drawer owns the toasts.
    edit() {
      this.$emit('edit', this.campaign);
    },
    publish() {
      this.callAction(this.$cleanupService.publishCampaign(this.campaignId), 'publish');
    },
    execute() {
      this.callAction(this.$cleanupService.executeCampaign(this.campaignId), 'execute');
    },
    cancel() {
      this.callAction(this.$cleanupService.cancelCampaign(this.campaignId), 'cancel');
    },
    // Requeues the retryable failures. WHICH ones are retryable is the server's
    // call, never re-derived here — and it has nothing to do with the
    // RETRYABLE_FAILURE_REASONS of CleanupUtils, which qualify the USER's bulk
    // keep/un-keep refusals, a different set of codes entirely.
    //
    // The endpoint answers the updated campaign, back to EXECUTING, so it is
    // applied instead of refetched. Nothing else is needed for the run to read
    // as live: 'running' already covers EXECUTING, so the refreshNeeded watcher
    // re-arms the fallback poll, and the CometD handlers key on the campaign id
    // rather than on the state they were registered in.
    retry() {
      this.callAction(this.$cleanupService.retryCampaign(this.campaignId), 'retry', true);
    },
    // 'applyResult' is for the endpoints answering the updated campaign (retry):
    // it is applied directly, where a void endpoint still needs the extra GET
    callAction(promise, action, applyResult) {
      this.actionInProgress = true;
      return promise
        .then(campaign => {
          this.displayAlert(this.$t(`cleanup.admin.campaign.${action}Success`));
          return applyResult ? this.applyCampaign(campaign) : this.loadCampaign();
        })
        .then(() => this.$emit('refresh'))
        // The REST layer answers a MESSAGE CODE as the error body
        // (cleanup.graceNotElapsed, cleanup.campaignAlreadyActive...): localized
        // by the shared $cleanupErrorLabel, never dropped raw in the toast
        .catch(error => {
          const reason = this.$cleanupErrorLabel(error, UNEXPECTED_ERROR_KEY);
          this.displayAlert(this.$t(`cleanup.admin.campaign.${action}Error`, {0: reason}), 'error');
        })
        .finally(() => this.actionInProgress = false);
    },
    displayAlert(message, type) {
      document.dispatchEvent(new CustomEvent('notification-alert', {detail: {
        message,
        type: type || 'success',
      }}));
    },
  }
};
</script>
