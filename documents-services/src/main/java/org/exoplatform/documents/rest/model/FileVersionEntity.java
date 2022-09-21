package org.exoplatform.documents.rest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileVersionEntity {

  private String  id;

  private String  frozenId;

  private String  originId;

  private int     versionNumber;

  private String  title;

  private String  summary;

  private String  author;

  private String  authorFullName;

  private Date    createdDate;

  private boolean isCurrent;

  private long    size;
}
