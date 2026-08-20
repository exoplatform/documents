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
  <exo-drawer
    id="cleanupCampaignCreateDrawer"
    ref="drawer"
    :loading="loading"
    right>
    <template slot="title">
      {{ $t(editMode ? 'cleanup.admin.editDrawer.title' : 'cleanup.admin.createDrawer.title') }}
    </template>
    <template slot="content">
      <v-form ref="form" class="pa-5">
        <v-label for="cleanupCampaignName">{{ $t('cleanup.admin.createDrawer.name') }}</v-label>
        <v-text-field
          id="cleanupCampaignName"
          v-model="name"
          :rules="[v => !!v || $t('cleanup.admin.createDrawer.nameRequired')]"
          :maxlength="nameMaxLength"
          :aria-label="$t('cleanup.admin.createDrawer.name')"
          class="pt-2 pb-4"
          outlined
          dense
          required />
        <!-- A disabled field with no explanation reads as a bug, so the reason
             is spelled out ONCE here rather than as a tooltip per input: a
             disabled Vuetify input does not reliably emit the mouse events a
             tooltip listens to. The hint names the four frozen criteria
             explicitly because the grace period sits among them and stays
             editable -->
        <div v-if="editMode" class="d-flex align-start text-light-color caption mb-4">
          <v-icon size="14" class="me-2 mt-1">fas fa-info-circle</v-icon>
          <span>{{ $t('cleanup.admin.editDrawer.frozenCriteriaHint') }}</span>
        </div>
        <v-label for="cleanupPeriodMonths" class="mt-2">{{ $t('cleanup.admin.createDrawer.periodMonths') }}</v-label>
        <v-text-field
          id="cleanupPeriodMonths"
          v-model.number="periodMonths"
          :disabled="criteriaFrozen"
          :aria-label="$t('cleanup.admin.createDrawer.periodMonths')"
          type="number"
          min="1"
          class="pt-2"
          outlined
          dense />
        <!-- This field governs TWO destructive rules, not one: which files become
             candidates, and which versions get deleted. Lowering it to catch more
             stale files also deletes more version history, which no label on a
             number input conveys — so it is spelled out here, before Launch -->
        <div class="d-flex align-start text-light-color caption pb-4">
          <v-icon size="14" class="me-2 mt-1">fas fa-info-circle</v-icon>
          <span>{{ $t('cleanup.admin.createDrawer.periodMonthsHint') }}</span>
        </div>
        <v-label for="cleanupMinFileSize" class="mt-2">{{ $t('cleanup.admin.createDrawer.minFileSizeMb') }}</v-label>
        <v-text-field
          id="cleanupMinFileSize"
          v-model.number="minFileSizeMb"
          :disabled="criteriaFrozen"
          :aria-label="$t('cleanup.admin.createDrawer.minFileSizeMb')"
          type="number"
          min="0"
          class="pt-2 pb-4"
          outlined
          dense />
        <v-label for="cleanupGraceDays" class="mt-2">{{ $t('cleanup.admin.createDrawer.graceDays') }}</v-label>
        <!-- The `min` is not always 0: on a PUBLISHED campaign the server only
             accepts an EXTENSION (cleanup.graceDaysCannotBeReduced), a deadline
             having been promised to the owners of the candidate files. Bound AND
             validated, because a number input's `min` only governs its spinner
             — a typed or pasted value goes straight through it -->
        <v-text-field
          id="cleanupGraceDays"
          v-model.number="graceDays"
          :disabled="!graceEditable"
          :aria-label="$t('cleanup.admin.createDrawer.graceDays')"
          :min="graceDaysMin"
          :rules="graceDaysRules"
          type="number"
          class="pt-2 pb-4"
          outlined
          dense />
        <!-- What the typed number MEANS, live: the deadline it produces. Turns
             '14' into a decision instead of arithmetic the administrator does
             in their head -->
        <div v-if="deadlineShown" class="d-flex flex-column mt-n4 mb-4">
          <div class="d-flex align-center flex-wrap">
            <span class="text-light-color caption me-1">{{ $t('cleanup.admin.editDrawer.resultingDeadline') }}</span>
            <date-format
              :value="resultingDeadline"
              :format="$cleanupUtils.DATE_TIME_FORMAT"
              class="caption text-color font-weight-bold" />
          </div>
          <!-- Accepted by the server on purpose, but never a silent outcome:
               the review window closes at once and the campaign locks at the
               next cron tick -->
          <div v-if="deadlineElapsed" class="d-flex align-start warning--text caption mt-1">
            <v-icon size="14" class="me-1 mt-1">fas fa-exclamation-triangle</v-icon>
            <span>{{ $t('cleanup.admin.editDrawer.deadlineInPast') }}</span>
          </div>
        </div>
        <div v-if="graceFrozen" class="d-flex align-start text-light-color caption mt-n4 mb-4">
          <v-icon size="14" class="me-2 mt-1">fas fa-info-circle</v-icon>
          <span>{{ $t('cleanup.admin.editDrawer.graceLockedHint') }}</span>
        </div>
        <v-label for="cleanupMaxVersions" class="mt-2">{{ $t('cleanup.admin.createDrawer.maxVersionsPerFile') }}</v-label>
        <v-text-field
          id="cleanupMaxVersions"
          v-model.number="maxVersionsPerFile"
          :disabled="criteriaFrozen"
          :aria-label="$t('cleanup.admin.createDrawer.maxVersionsPerFile')"
          type="number"
          min="1"
          class="pt-2 pb-4"
          outlined
          dense />
        <v-label for="cleanupExcludedPaths" class="mt-2">{{ $t('cleanup.admin.createDrawer.excludedPaths') }}</v-label>
        <v-textarea
          id="cleanupExcludedPaths"
          v-model="excludedPathsText"
          :placeholder="$t('cleanup.admin.createDrawer.excludedPathsPlaceholder')"
          :disabled="criteriaFrozen"
          :aria-label="$t('cleanup.admin.createDrawer.excludedPaths')"
          rows="3"
          class="extended-textarea mt-n2"
          auto-grow />
      </v-form>
    </template>
    <template slot="footer">
      <div class="d-flex justify-end">
        <v-btn class="btn me-2" @click="close">
          {{ $t('cleanup.admin.createDrawer.cancel') }}
        </v-btn>
        <!-- Nothing changed means nothing to send: the button is disabled
             rather than firing a PATCH that would only come back
             'cleanup.nothingToUpdate'. Wrapped in a div because a disabled
             v-btn emits no mouse event, so it would never open its own
             tooltip (same trick as the campaign header buttons) -->
        <v-tooltip
          v-if="editMode"
          :disabled="hasChanges"
          bottom>
          <template #activator="{on, attrs}">
            <div v-bind="attrs" v-on="on">
              <v-btn
                :aria-label="$t('cleanup.admin.editDrawer.save')"
                :disabled="!hasChanges"
                :loading="loading"
                class="btn btn-primary"
                @click="save">
                {{ $t('cleanup.admin.editDrawer.save') }}
              </v-btn>
            </div>
          </template>
          <span>{{ $t('cleanup.admin.editDrawer.nothingChanged') }}</span>
        </v-tooltip>
        <v-btn
          v-else
          :loading="loading"
          class="btn btn-primary"
          @click="save">
          {{ $t('cleanup.admin.createDrawer.launch') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
const MEGA_BYTE = 1048576;
const DAY_MILLIS = 86400000;
// Same generic fallback as the campaign actions: a code with no bundle entry is
// never shown raw
const UNEXPECTED_ERROR_KEY = 'cleanup.admin.campaign.unexpectedError';
// Mirrors CleanupCampaignService.MAX_NAME_LENGTH, itself mirroring the NAME
// column (NVARCHAR(250)): the field is capped client-side so the administrator
// cannot type a name the server would only refuse on submit with
// 'cleanup.nameTooLong'
const NAME_MAX_LENGTH = 250;
// The ONLY states the grace period is updatable in, kept in ONE place and
// mirroring the SERVER's authority (it answers 'cleanup.invalidState'
// otherwise): past PUBLISHED the review window is closed or the purge already
// ran, so a grace period no longer means anything. This list never decides
// anything — it only avoids OFFERING an edit that would be refused. The NAME,
// by contrast, is pure metadata and stays editable in every state.
const GRACE_EDITABLE_STATES = ['DRAFT', 'SIMULATED', 'PUBLISHED'];

export default {
  data() {
    return {
      loading: false,
      // The campaign being EDITED, null while creating: the single flag telling
      // the two modes of this drawer apart
      campaign: null,
      name: '',
      periodMonths: null,
      minFileSizeMb: null,
      graceDays: null,
      maxVersionsPerFile: null,
      excludedPathsText: '',
      // Local clock read taken when the drawer opened, against which the
      // resulting deadline is told past or future. DISPLAY ONLY — the Execute
      // gate keeps using the server's own 'executable' / 'remainingMillis', so
      // a skewed browser can at worst mis-hint by minutes here, never unlock
      // an action the server would refuse
      openedAt: Date.now(),
    };
  },
  computed: {
    editMode() {
      return !!this.campaign;
    },
    nameMaxLength() {
      return NAME_MAX_LENGTH;
    },
    // The dry run computed its candidates WITH these criteria and snapshotted
    // them: editing them afterwards would make the report describe rules it was
    // never computed with
    criteriaFrozen() {
      return this.editMode;
    },
    graceEditable() {
      return !this.editMode || GRACE_EDITABLE_STATES.includes(this.campaign.state);
    },
    graceFrozen() {
      return this.editMode && !this.graceEditable;
    },
    // A PUBLISHED grace period is ONE-WAY: the server refuses any value below
    // the published one (cleanup.graceDaysCannotBeReduced), so the drawer must
    // not OFFER one. Everywhere else the floor is the plain 0 the field always
    // had — 0 being a real grace period, not an absent one
    graceDaysMin() {
      return this.editMode && this.campaign.state === 'PUBLISHED' && this.campaign.graceDays != null
        ? this.campaign.graceDays
        : 0;
    },
    // The SERVER's message codes, localized here, in the SERVER's own check
    // order — the bound first, then the direction — so the field and the
    // response never name different reasons for the same value. An empty field
    // is left to the platform default, hence no rule on null
    graceDaysRules() {
      return [v => {
        const graceDays = this.numberOrNull(v);
        if (graceDays === null) {
          return true;
        }
        if (graceDays < 0) {
          return this.$t('cleanup.invalidGraceDays');
        }
        return graceDays >= this.graceDaysMin || this.$t('cleanup.graceDaysCannotBeReduced');
      }];
    },
    // Only PUBLISHED has a deadline to project: before publication there is no
    // start instant to add the grace period to
    deadlineShown() {
      return this.editMode && this.campaign.state === 'PUBLISHED' && this.resultingDeadline !== null;
    },
    resultingDeadline() {
      const graceDays = this.numberOrNull(this.graceDays);
      if (graceDays === null || graceDays < 0 || !this.campaign?.publishedDate) {
        return null;
      }
      return this.campaign.publishedDate + graceDays * DAY_MILLIS;
    },
    deadlineElapsed() {
      return this.resultingDeadline !== null && this.resultingDeadline <= this.openedAt;
    },
    // ONLY what actually changed, because the endpoint is a PARTIAL update: an
    // attribute left out is left untouched, so sending the untouched ones back
    // would overwrite what nobody edited
    changes() {
      if (!this.editMode) {
        return {};
      }
      const changes = {};
      const name = this.name?.trim();
      if (name && name !== this.campaign.name) {
        changes.name = name;
      }
      const graceDays = this.numberOrNull(this.graceDays);
      // Compared to null, NEVER tested for falsiness: 0 is a real grace period
      // (it elapses at publication), so a deliberate 0 must reach the server
      if (this.graceEditable && graceDays !== null && graceDays !== this.campaign.graceDays) {
        changes.graceDays = graceDays;
      }
      return changes;
    },
    hasChanges() {
      return Object.keys(this.changes).length > 0;
    },
  },
  methods: {
    // open() creates, open(campaign) edits that campaign: one drawer for both,
    // so the name and the grace period are validated and reported in exactly
    // one place
    open(campaign) {
      this.campaign = campaign || null;
      this.openedAt = Date.now();
      if (this.campaign) {
        // The campaign's OWN parameters, never the platform defaults: the
        // frozen criteria have to be shown as the dry run computed them with
        this.name = campaign.name || '';
        this.periodMonths = campaign.periodMonths;
        this.minFileSizeMb = this.toMegaBytes(campaign.minFileSizeBytes);
        this.graceDays = campaign.graceDays;
        this.maxVersionsPerFile = campaign.maxVersionsPerFile;
        this.excludedPathsText = (campaign.excludedPaths || []).join('\n');
        this.loading = false;
        this.$refs.drawer.open();
        return;
      }
      this.name = '';
      this.loading = true;
      this.$refs.drawer.open();
      this.$cleanupService.getDefaults()
        .then(defaults => {
          this.periodMonths = defaults?.periodMonths;
          this.minFileSizeMb = this.toMegaBytes(defaults?.minFileSizeBytes);
          this.graceDays = defaults?.graceDays;
          this.maxVersionsPerFile = defaults?.maxVersionsPerFile;
          this.excludedPathsText = (defaults?.excludedPaths || []).join('\n');
        })
        .catch(() => this.displayAlert(this.$t('cleanup.admin.createDrawer.defaultsError'), 'error'))
        .finally(() => this.loading = false);
    },
    close() {
      this.$refs.drawer.close();
    },
    // One submit path for both modes — same validation, same loading flag, same
    // localized-code error handling — branching only on the request to fire and
    // on the messages to report. The drawer is closed on SUCCESS only: a
    // refusal keeps everything typed on screen so it can be corrected.
    save() {
      if (!this.$refs.form.validate()) {
        return;
      }
      const editMode = this.editMode;
      if (editMode && !this.hasChanges) {
        // Guards the programmatic path: the submit button is already disabled
        // in this case, and firing the PATCH would only bring
        // 'cleanup.nothingToUpdate' back from the server
        this.displayAlert(this.$t('cleanup.admin.editDrawer.nothingChanged'), 'warning');
        return;
      }
      this.loading = true;
      const request = editMode
        ? this.$cleanupService.updateCampaign(this.campaign.id, this.changes)
        : this.$cleanupService.createCampaign(this.creationPayload());
      request.then(campaign => {
        this.displayAlert(this.$t(editMode ? 'cleanup.admin.editDrawer.success' : 'cleanup.admin.createDrawer.success'));
        // The endpoint answers the full campaign: emitted so the caller applies
        // it instead of refetching
        this.$emit(editMode ? 'updated' : 'created', campaign);
        this.close();
      }).catch(error => {
        // Creation is the ONLY producer of cleanup.campaignAlreadyActive (one
        // active campaign platform-wide — the likeliest refusal there) and of
        // the cleanup.invalidPeriodMonths / cleanup.invalidMinFileSize /
        // cleanup.invalidMaxVersionsPerFile validation codes; the update path
        // answers cleanup.campaignNotFound, cleanup.nothingToUpdate,
        // cleanup.invalidState or cleanup.graceDaysCannotBeReduced (the field
        // rule above front-runs that last one, but a stale campaign snapshot
        // still lets the server have the final word), and both share
        // cleanup.nameMandatory / cleanup.nameTooLong /
        // cleanup.invalidGraceDays. Discarding the error made every one of those
        // bundle entries unreachable.
        const reason = this.$cleanupErrorLabel(error, UNEXPECTED_ERROR_KEY);
        this.displayAlert(this.$t(editMode ? 'cleanup.admin.editDrawer.error' : 'cleanup.admin.createDrawer.error', {0: reason}), 'error');
      }).finally(() => this.loading = false);
    },
    // The creation body, unchanged: every criterion is sent, a cleared one as
    // null so the platform default applies
    creationPayload() {
      const excludedPaths = this.excludedPathsText
        .split('\n')
        .map(path => path.trim())
        .filter(path => !!path);
      const minFileSizeMb = this.numberOrNull(this.minFileSizeMb);
      return {
        name: this.name,
        periodMonths: this.numberOrNull(this.periodMonths),
        minFileSizeBytes: minFileSizeMb === null ? null : Math.round(minFileSizeMb * MEGA_BYTE),
        graceDays: this.numberOrNull(this.graceDays),
        maxVersionsPerFile: this.numberOrNull(this.maxVersionsPerFile),
        excludedPaths,
      };
    },
    toMegaBytes(bytes) {
      // Compared to null, NEVER tested for falsiness: 0 is a REAL minimum size
      // (no minimum at all), and so is every size under ~5 KB, which rounds to
      // 0 MB here. `x || null` turned all of them into a blank field, so a
      // deliberately-set minimum was indistinguishable from an unset one
      return bytes == null ? null : Math.round(bytes * 100 / MEGA_BYTE) / 100;
    },
    numberOrNull(value) {
      // A cleared numeric input yields '' (and '' * N === 0): treat '' / null
      // / undefined as 'no override' so the platform default applies instead
      // of a spurious 0
      return value === '' || value == null || isNaN(value) ? null : Number(value);
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
