package org.exoplatform.documents.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileVersion implements Comparable<FileVersion> {

  private String  id;

  private int     versionNumber;

  private String  title;

  private String  summary;

  private String  author;

  private String  authorFullName;

  private Date    createdDate;

  private boolean isCurrent;

  private long    size;

  @Override
  public int compareTo(FileVersion fileVersion) {
    return createdDate.compareTo(fileVersion.createdDate);
  }
}
