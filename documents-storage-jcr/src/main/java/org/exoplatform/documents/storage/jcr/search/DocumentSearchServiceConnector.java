/*
 * Copyright (C) 2021 eXo Platform SAS
 *  
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.storage.jcr.search;

import java.util.*;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;

import org.exoplatform.commons.api.search.data.SearchContext;
import org.exoplatform.commons.api.search.data.SearchResult;
import org.exoplatform.commons.search.es.ElasticSearchFilter;
import org.exoplatform.commons.search.es.ElasticSearchFilterType;
import org.exoplatform.commons.search.es.ElasticSearchServiceConnector;
import org.exoplatform.commons.search.es.client.ElasticSearchingClient;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.documents.model.DocumentTimelineFilter;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.metadata.favorite.FavoriteService;
import org.exoplatform.web.controller.metadata.ControllerDescriptor;
import org.exoplatform.web.controller.router.Router;

public class DocumentSearchServiceConnector extends ElasticSearchServiceConnector {

  private static final Log      LOG               = ExoLogger.getLogger(DocumentSearchServiceConnector.class);

  private ThreadLocal<String>   filteredWorkspace = new ThreadLocal<>();

  private ThreadLocal<String>   filteredPath      = new ThreadLocal<>();

  private ThreadLocal<Identity> aclIdentity       = new ThreadLocal<>();

  public DocumentSearchServiceConnector(ElasticSearchingClient client, InitParams initParams) {
    super(initParams, client);
  }

  @Override
  protected String getSourceFields() {
    return "\"name\",\"workspace\",\"path\"";
  }

  @Override
  protected String getFields() {
    return "\"title\"";
  }

  public Collection<SearchResult> appSearch(Identity userIdentity, // NOSONAR
                                            String workspace,
                                            String path,
                                            DocumentTimelineFilter filter,
                                            int offset,
                                            int limit,
                                            String sort,
                                            String order) {
    aclIdentity.set(userIdentity);
    filteredWorkspace.set(workspace);
    if (StringUtils.isNotEmpty(path)) {
      if (path.endsWith("/")) {
        filteredPath.set(path);
      } else {
        filteredPath.set(path + "/");
      }
    }

    try {
      SearchContext context = new SearchContext(new Router(new ControllerDescriptor()), "");
      context.lang(Locale.ENGLISH.toString());
      List<ElasticSearchFilter> recentFilters = new ArrayList<>();
      if (BooleanUtils.isTrue(filter.getFavorites())) {
        Map<String, List<String>> metadataFilters = buildMetadataFilter();
        String metadataQuery = buildMetadataQueryStatement(metadataFilters);
        StringBuilder recentFilter = new StringBuilder();
        recentFilter.append(metadataQuery);
        recentFilters.add(new ElasticSearchFilter(ElasticSearchFilterType.FILTER_MATADATAS, "", recentFilter.toString()));
      }
      return super.filteredSearch(context, filter.getQuery(), recentFilters, null, offset, limit, sort, order);
    } catch (Exception ex) {
      LOG.error("Can not create SearchContext", ex);
      return Collections.emptyList();
    } finally {
      aclIdentity.remove();
      filteredWorkspace.remove();
      filteredPath.remove();
    }
  }

  @Override
  protected String getAdditionalFilters(List<ElasticSearchFilter> filters) {
    String workspace = filteredWorkspace.get();
    String path = filteredPath.get();
    if (StringUtils.isNotEmpty(workspace)) {
      filters = addFilter(filters, new ElasticSearchFilter(ElasticSearchFilterType.FILTER_BY_TERM, "workspace", workspace));
    }

    if (StringUtils.isNotEmpty(path)) {
      String titleFilterQuery = "{ " + " \"prefix\" : { " + "  \"path\" :  { " + "   \"value\" : \"" + path + "\" " + "  } "
          + " } " + "}";
      filters = addFilter(filters, new ElasticSearchFilter(ElasticSearchFilterType.FILTER_CUSTOM, "", titleFilterQuery));
    }
    return super.getAdditionalFilters(filters);
  }

  @Override
  protected SearchResult buildHit(JSONObject jsonHit, SearchContext searchContext) {
    SearchResult searchResult = super.buildHit(jsonHit, searchContext);
    JSONObject hitSource = (JSONObject) jsonHit.get("_source");
    String id = (String) jsonHit.get("_id");
    String workspace = (String) hitSource.get("workspace");
    String nodePath = (String) hitSource.get("path");
    return new DocumentFileSearchResult(searchResult, id, workspace, nodePath);
  }

  @Override
  protected Set<String> getUserMemberships() {
    Identity userAclIdentity = aclIdentity.get();
    Set<String> entries = new HashSet<>();
    for (MembershipEntry entry : userAclIdentity.getMemberships()) {
      // If it's a wildcard membership, add a point to transform it to regexp
      if (entry.getMembershipType().equals(MembershipEntry.ANY_TYPE)) {
        entries.add(entry.toString().replace("*", ".*"));
      }
      // If it's not a wildcard membership
      else {
        // Add the membership
        entries.add(entry.toString());
        // Also add a wildcard membership (not as a regexp) in order to match to
        // wildcard permission
        // Ex: membership dev:/pub must match permission dev:/pub and permission
        // *:/pub
        entries.add("*:" + entry.getGroup());
      }
    }
    return entries;
  }

  private List<ElasticSearchFilter> addFilter(List<ElasticSearchFilter> filters, ElasticSearchFilter elasticSearchFilter) {
    if (filters == null) {
      filters = new ArrayList<>();
    }
    filters.add(elasticSearchFilter);
    return filters;
  }

  private String buildMetadataQueryStatement(Map<String, List<String>> metadataFilters) {
    StringBuilder metadataQuerySB = new StringBuilder();
    Set<Map.Entry<String, List<String>>> metadataFilterEntries = metadataFilters.entrySet();
    for (Map.Entry<String, List<String>> metadataFilterEntry : metadataFilterEntries) {
      metadataQuerySB.append("{\"terms\":{\"metadatas.")
                     .append(metadataFilterEntry.getKey())
                     .append(".metadataName.keyword")
                     .append("\": [\"")
                     .append(org.apache.commons.lang.StringUtils.join(metadataFilterEntry.getValue(), "\",\""))
                     .append("\"]}},");
    }
    return metadataQuerySB.toString();
  }

  private Map<String, List<String>> buildMetadataFilter() {
    org.exoplatform.social.core.identity.model.Identity viewerIdentity =
                                                                       CommonsUtils.getService(IdentityManager.class)
                                                                                   .getOrCreateIdentity(OrganizationIdentityProvider.NAME,
                                                                                                        aclIdentity.get()
                                                                                                                   .getUserId());
    Map<String, List<String>> metadataFilters = new HashMap<>();
    metadataFilters.put(FavoriteService.METADATA_TYPE.getName(), Collections.singletonList(viewerIdentity.getId()));
    return metadataFilters;
  }

}
