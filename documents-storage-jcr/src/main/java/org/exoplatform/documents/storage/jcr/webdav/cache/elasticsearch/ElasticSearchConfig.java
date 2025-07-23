/*
 * Copyright (C) 2003 - 2025 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch;

import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.ClientConfiguration.ClientConfigurationBuilderWithRequiredEndpoint;
import org.springframework.data.elasticsearch.client.ClientConfiguration.MaybeSecureClientConfigurationBuilder;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.RefreshPolicy;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

@Configuration
public class ElasticSearchConfig extends ElasticsearchConfiguration {

  @Value("${exo.es.search.server.username:}")
  private String esUsername;

  @Value("${exo.es.search.server.password:}")
  private String esPassword;

  @Value("${exo.es.search.server.url:http://127.0.0.1:9200}")
  private String esUrl;

  @Value("${exo.es.search.socketTimeout:20}")
  private int    socketTimeout;

  @Value("${exo.es.search.connectTimeout:20}")
  private int    connectionTimeout;

  @Value("${exo.es.search.connectionRetry:500}")
  private int    connectionRetry;

  @Override
  public ClientConfiguration clientConfiguration() {
    String hostAndPort = esUrl.split("//")[1];
    ClientConfigurationBuilderWithRequiredEndpoint builder = ClientConfiguration.builder();
    MaybeSecureClientConfigurationBuilder connectionBuilder = builder.connectedTo(hostAndPort);
    if (esUrl.contains("https://")) {
      connectionBuilder.usingSsl();
    }
    if (StringUtils.isNotBlank(esPassword) && StringUtils.isNotBlank(esUsername)) {
      connectionBuilder.withBasicAuth(esUsername, esPassword);
    }
    connectionBuilder.withConnectTimeout(Duration.ofSeconds(connectionTimeout));
    connectionBuilder.withSocketTimeout(Duration.ofSeconds(socketTimeout));
    return connectionBuilder.build();
  }

  @Override
  public ElasticsearchOperations elasticsearchOperations(ElasticsearchConverter elasticsearchConverter,
                                                         ElasticsearchClient elasticsearchClient) {
    ElasticsearchTemplate elasticsearchTemplate = new ElasticsearchTemplate(elasticsearchClient, elasticsearchConverter);
    elasticsearchTemplate.setRefreshPolicy(RefreshPolicy.IMMEDIATE);
    return elasticsearchTemplate;
  }

}
