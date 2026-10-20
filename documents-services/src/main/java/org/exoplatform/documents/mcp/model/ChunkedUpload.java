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
package org.exoplatform.documents.mcp.model;

import java.nio.file.Path;
import java.time.Instant;

import org.exoplatform.documents.constant.UploadConflictAction;

/**
 * An upload in pieces as the store of the uploads in progress holds it: who
 * started it, where and how it is filed, the temporary file holding its
 * pieces, and, once filed, the document it became.
 *
 * @param id its id
 * @param userName the user who started it
 * @param folderId the folder it is filed into
 * @param name the file name it is filed under
 * @param size the file's size, in bytes, announced at its start
 * @param conflictAction what to do when the folder holds a file of that name
 * @param file the temporary file holding the pieces received
 * @param received the bytes received
 * @param nextIndex the number of the next piece expected
 * @param lastPieceDigest the SHA-256 of the last piece received, hex-encoded,
 *          to acknowledge it sent again and refuse another piece under its
 *          number
 * @param touched when it was last used
 * @param filing whether its last piece came and its file is being filed
 * @param document the document it was filed as, once filed
 */
public record ChunkedUpload(String id,
                            String userName,
                            String folderId,
                            String name,
                            long size,
                            UploadConflictAction conflictAction,
                            Path file,
                            long received,
                            int nextIndex,
                            String lastPieceDigest,
                            Instant touched,
                            boolean filing,
                            DocumentModel document) {

  /**
   * The upload with a piece more.
   *
   * @param length the piece's length
   * @param digest the piece's SHA-256, hex-encoded
   * @param now when it came
   * @return the upload, filing once all its bytes came
   */
  public ChunkedUpload withPiece(int length, String digest, Instant now) {
    long total = received + length;
    return new ChunkedUpload(id,
                             userName,
                             folderId,
                             name,
                             size,
                             conflictAction,
                             file,
                             total,
                             nextIndex + 1,
                             digest,
                             now,
                             total == size,
                             null);
  }

  /**
   * The upload used at another time.
   *
   * @param now when it was used
   * @return the upload, marked
   */
  public ChunkedUpload touchedAt(Instant now) {
    return new ChunkedUpload(id,
                             userName,
                             folderId,
                             name,
                             size,
                             conflictAction,
                             file,
                             received,
                             nextIndex,
                             lastPieceDigest,
                             now,
                             filing,
                             document);
  }

  /**
   * The upload filed.
   *
   * @param filed the document it was filed as
   * @param now when it was filed
   * @return the upload, filed
   */
  public ChunkedUpload filedAs(DocumentModel filed, Instant now) {
    return new ChunkedUpload(id,
                             userName,
                             folderId,
                             name,
                             size,
                             conflictAction,
                             file,
                             received,
                             nextIndex,
                             lastPieceDigest,
                             now,
                             false,
                             filed);
  }

}
