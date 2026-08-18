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

import CleanupReviewHeaderButton from './components/CleanupReviewHeaderButton.vue';
import CleanupUserReview from './components/CleanupUserReview.vue';
import ReviewSummaryBanner from './components/ReviewSummaryBanner.vue';
import ReviewItemsList from './components/ReviewItemsList.vue';
import ReviewOutcome from './components/ReviewOutcome.vue';

const components = {
  'document-cleanup-review-header-button': CleanupReviewHeaderButton,
  'document-cleanup-user-review': CleanupUserReview,
  'document-cleanup-review-summary-banner': ReviewSummaryBanner,
  'document-cleanup-review-items-list': ReviewItemsList,
  'document-cleanup-review-outcome': ReviewOutcome,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
