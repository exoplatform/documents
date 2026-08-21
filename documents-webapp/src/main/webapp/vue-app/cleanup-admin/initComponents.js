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

import CleanupAdmin from './components/CleanupAdmin.vue';
import CampaignList from './components/CampaignList.vue';
import CampaignCreateDrawer from './components/CampaignCreateDrawer.vue';
import CampaignDetail from './components/CampaignDetail.vue';
import CampaignScanUnits from './components/CampaignScanUnits.vue';
import CampaignStats from './components/CampaignStats.vue';
import CampaignItemsTable from './components/CampaignItemsTable.vue';
import CampaignCompareView from './components/CampaignCompareView.vue';

const components = {
  'document-cleanup-admin': CleanupAdmin,
  'document-cleanup-campaign-list': CampaignList,
  'document-cleanup-campaign-create-drawer': CampaignCreateDrawer,
  'document-cleanup-campaign-detail': CampaignDetail,
  'document-cleanup-campaign-stats': CampaignStats,
  'document-cleanup-campaign-scan-units': CampaignScanUnits,
  'document-cleanup-campaign-items-table': CampaignItemsTable,
  'document-cleanup-campaign-compare-view': CampaignCompareView,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
