/*
 * Copyright (C) 2003-2021 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.documents.storage.jcr.search;

import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;

import org.exoplatform.commons.api.search.data.SearchContext;
import org.exoplatform.commons.api.search.data.SearchResult;
import org.exoplatform.commons.search.es.*;
import org.exoplatform.commons.search.es.client.ElasticSearchingClient;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.web.controller.metadata.ControllerDescriptor;
import org.exoplatform.web.controller.router.Router;

public class DocumentSearchServiceConnector extends ElasticSearchServiceConnector {

  private static final Log      LOG               = ExoLogger.getLogger(DocumentSearchServiceConnector.class);

  private ThreadLocal<String>   filteredWorkspace = new ThreadLocal<>();

  private ThreadLocal<String>   filteredPath      = new ThreadLocal<>();

  private ThreadLocal<Identity> aclIdentity       = new ThreadLocal<>();

  public DocumentSearchServiceConnector(ElasticSearchingClient client,
                                        InitParams initParams) {
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
                                            String query,
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
      return super.search(context, query, null, offset, limit, sort, order);
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
      filters =
              addFilter(filters,
                        new ElasticSearchFilter(ElasticSearchFilterType.FILTER_BY_TERM,
                                                "workspace",
                                                workspace));
    }

    if (StringUtils.isNotEmpty(path)) {
      String titleFilterQuery = "{ " +
          " \"prefix\" : { " +
          "  \"path\" :  { " +
          "   \"value\" : \"" + path + "\" " +
          "  } " +
          " } " +
          "}";
      filters = addFilter(filters,
                          new ElasticSearchFilter(ElasticSearchFilterType.FILTER_CUSTOM,
                                                  "",
                                                  titleFilterQuery));
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

}
