package org.exoplatform.documents.storage.jcr.search;

import org.exoplatform.documents.legacy.search.data.SearchResult;

public class DocumentFileSearchResult extends SearchResult {

  public DocumentFileSearchResult(SearchResult result) {
    super(result.getUrl(),
          result.getTitle(),
          result.getExcerpt(),
          result.getDetail(),
          result.getImageUrl(),
          result.getDate(),
          result.getRelevancy());
  }

  public DocumentFileSearchResult(SearchResult result, String id, String workspace, String nodePath) {
    super(result.getUrl(),
          result.getTitle(),
          result.getExcerpt(),
          result.getDetail(),
          result.getImageUrl(),
          result.getDate(),
          result.getRelevancy());
    this.id = id;
    this.workspace = workspace;
    this.nodePath = nodePath;
  }

  private String id;

  private String workspace;

  private String nodePath;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getWorkspace() {
    return workspace;
  }

  public void setWorkspace(String workspace) {
    this.workspace = workspace;
  }

  public String getNodePath() {
    return nodePath;
  }

  public void setNodePath(String nodePath) {
    this.nodePath = nodePath;
  }
}
