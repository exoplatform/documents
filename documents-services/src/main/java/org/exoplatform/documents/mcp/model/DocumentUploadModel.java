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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An upload in pieces, as {@code start_document_upload} and
 * {@code append_document_upload_chunk} return it.
 *
 * @param uploadId the upload's id, to send the pieces with
 * @param parentFolderId the folder the file is filed into
 * @param name the file name it is filed under
 * @param size the file's size, in bytes
 * @param receivedBytes the bytes received so far
 * @param nextIndex the number of the next piece to send, from 0
 * @param maxBytes the largest file uploaded
 * @param chunkChars the base64 characters of one piece to send, the last
 *          one holding the rest
 * @param expiresInMinutes how long the upload is kept without a piece
 * @param document the document the file was filed as, once its last piece
 *          came
 */
@JsonInclude(Include.NON_NULL)
public record DocumentUploadModel(@JsonProperty("upload_id") String uploadId,
                                  @JsonProperty("parent_folder_id") String parentFolderId,
                                  String name,
                                  long size,
                                  @JsonProperty("received_bytes") long receivedBytes,
                                  @JsonProperty("next_index") int nextIndex,
                                  @JsonProperty("max_bytes") long maxBytes,
                                  @JsonProperty("chunk_chars") int chunkChars,
                                  @JsonProperty("expires_in_minutes") long expiresInMinutes,
                                  DocumentModel document) {
}
