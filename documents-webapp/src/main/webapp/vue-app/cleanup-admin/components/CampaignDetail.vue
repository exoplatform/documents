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
      <!-- The name is pure metadata (no state transition, nothing keys off it),
           so it is editable in ANY state — terminal ones included: relabelling
           an already-completed report is a legitimate need. Inline field rather
           than a drawer: it edits one attribute -->
      <template v-if="renaming">
        <v-text-field
          ref="nameField"
          v-model="editedName"
          :disabled="renameInProgress"
          :maxlength="nameMaxLength"
          :aria-label="$t('cleanup.admin.campaign.name')"
          class="pa-0 ma-0 flex-grow-0"
          style="max-width: 320px"
          dense
          outlined
          hide-details
          @keyup.enter="saveName"
          @keyup.esc="cancelRename" />
        <v-btn
          :aria-label="$t('cleanup.admin.campaign.renameSave')"
          :loading="renameInProgress"
          class="ms-1"
          icon
          small
          @click="saveName">
          <v-icon size="14">fas fa-check</v-icon>
        </v-btn>
        <v-btn
          :aria-label="$t('cleanup.admin.campaign.renameCancel')"
          :disabled="renameInProgress"
          icon
          small
          @click="cancelRename">
          <v-icon size="14">fas fa-times</v-icon>
        </v-btn>
      </template>
      <template v-else>
        <h5 class="my-0 text-color font-weight-bold">{{ campaign.name }}</h5>
        <v-btn
          :aria-label="$t('cleanup.admin.campaign.rename')"
          class="ms-1"
          icon
          small
          @click="startRename">
          <v-icon size="14">fas fa-pencil-alt</v-icon>
        </v-btn>
      </template>
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
    <document-cleanup-campaign-stats :campaign="campaign" />
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
// Mirrors CleanupCampaignService.MAX_NAME_LENGTH, itself mirroring the NAME
// column (NVARCHAR(250)): the field is capped client-side so the admin cannot
// type a name the server would only refuse on submit
const NAME_MAX_LENGTH = 250;

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
      renaming: false,
      renameInProgress: false,
      editedName: '',
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
      // Execution failures grouped BY THE SERVER ([{reason, count, retryable}]),
      // plus the id of the campaign they were requested for: applyCampaign runs
      // on every load, and this must stay ONE request per completion
      failures: [],
      failuresLoadedFor: null,
    };
  },
  computed: {
    dateFields() {
      return DATE_FIELDS;
    },
    nameMaxLength() {
      return NAME_MAX_LENGTH;
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
      this.remainingMillis = campaign?.remainingMillis || 0;
      this.syncedAt = Date.now();
      this.now = this.syncedAt;
      this.syncFailures();
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
    startRename() {
      this.editedName = this.campaign?.name || '';
      this.renaming = true;
      // Focused on the next tick: the field is only rendered once 'renaming' is
      // applied
      this.$nextTick(() => this.$refs.nameField?.focus());
    },
    cancelRename() {
      this.renaming = false;
      this.editedName = '';
    },
    // An empty name is refused HERE too, not only through the 400 the server
    // answers: the round trip would report the very same message code for a
    // mistake the field can tell on the spot. Every other failure (including the
    // too-long name, which the maxlength cap already prevents) leaves the field
    // OPEN so the admin can correct it instead of losing what they typed.
    saveName() {
      if (this.renameInProgress) {
        return;
      }
      const name = this.editedName?.trim();
      if (!name) {
        this.renameFailed('cleanup.nameMandatory');
        return;
      }
      this.renameInProgress = true;
      return this.$cleanupService.renameCampaign(this.campaignId, name)
        .then(campaign => {
          // The endpoint answers the updated campaign: applied directly, so the
          // header and the list re-render without a refetch
          this.applyCampaign(campaign);
          this.renaming = false;
          this.editedName = '';
          this.displayAlert(this.$t('cleanup.admin.campaign.renameSuccess'));
          this.$emit('refresh');
        })
        .catch(error => this.renameFailed(error))
        .finally(() => this.renameInProgress = false);
    },
    // Same localization discipline as callAction: the REST layer answers a
    // MESSAGE CODE as the error body, localized through the shared
    // $cleanupErrorLabel and never dropped raw in the toast
    renameFailed(codeOrError) {
      const reason = this.$cleanupErrorLabel(codeOrError, UNEXPECTED_ERROR_KEY);
      this.displayAlert(this.$t('cleanup.admin.campaign.renameError', {0: reason}), 'error');
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
