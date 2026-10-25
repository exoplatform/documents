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
package org.exoplatform.documents.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.TranscriptionStatus;
import org.exoplatform.documents.constant.UploadConflictAction;
import org.exoplatform.documents.model.*;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.space.model.Space;

import io.meeds.portal.thumbnail.model.FileContent;

public interface DocumentFileStorage {

  /**
   * Retrieves the Root folder Path of a Space
   * 
   * @param spaceId {@link Space} technical identifier
   * @param aclIdentity user willing to access the space's root folder
   * @return the root {@link FolderNode} for the designated space
   * @throws ObjectNotFoundException when the space doesn't exist
   */
  FolderNode getSpaceRootFolder(long spaceId, Identity aclIdentity) throws ObjectNotFoundException;

  /**
   * Retrieves the Root folder Path of a user
   * 
   * @param aclIdentity user willing to access his/her own root folder
   * @return the root {@link FolderNode} for the designated user
   */
  FolderNode getPersonalRootFolder(Identity aclIdentity);

  /**
   * Return the root folder owner
   * {@link org.exoplatform.social.core.identity.model.Identity} id
   * 
   * @param documentId JCR Node Id by example
   * @return {@link org.exoplatform.social.core.identity.model.Identity} id
   */
  long getRootFolderOwnerId(String documentId);

  /**
   * Retrieves a list of accessible files, for a selected user, by applying the
   * designated filter. The returned results will be of type {@link FileNode}
   * only. The ownerId of filter object will be used to select the list of
   * accessible Nodes to retrieve switch a timeline.
   * 
   * @param filter {@link DocumentTimelineFilter} that contains filtering
   *          criteria
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param aclIdentity {@link Identity} of the user acessing files
   * @return {@link List} of {@link AbstractNode}
   * @throws ObjectNotFoundException when parentFolderId doesn't exisits
   */
  List<FileNode> getFilesTimeline(DocumentTimelineFilter filter,
                                  Identity aclIdentity,
                                  int offset,
                                  int limit) throws ObjectNotFoundException;

  /**
   * @param keyword Search term
   * @param identity User ACL {@link org.exoplatform.services.security.Identity}
   * @param offset search offset
   * @param limit search limit
   * @return {@link List} of found {@link FileNode} available for user
   */
  List<FileNode> search(String keyword, Identity identity, int offset, int limit);

  /**
   * @param filter {@link DocumentTimelineFilter} to restrict search results
   * @param identity User ACL {@link org.exoplatform.services.security.Identity}
   * @param offset search offset
   * @param limit search limit
   * @return {@link List} of found {@link FileNode} available for user
   */
  List<FileNode> search(DocumentTimelineFilter filter, Identity identity, int offset, int limit);

  /**
   * Retrieves a list of biggest accessible files, for a selected user.
   * The returned results will be of type {@link FileNode}
   * only. The ownerId of filter object will be used to select the list of
   * accessible Nodes to retrieve.
   *
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param aclIdentity {@link Identity} of the user acessing files
   * @param ownerId {@link Identity} of the identity in which search files
   * @return {@link List} of {@link AbstractNode}
   * @throws ObjectNotFoundException when parentFolderId doesn't exisits
   */
  List<AbstractNode> getBiggestDocuments(Long ownerId,
                                 Identity aclIdentity,
                                 int offset,
                                 int limit) throws ObjectNotFoundException;

  long calculateFilesSize(Long ownerId, Identity aclIdentity) throws ObjectNotFoundException;

  /**
   * Retrieves the number of existing files by group.
   *
   * @param filter {@link DocumentFolderFilter} that contains filtering criteria
   * @param aclIdentity {@link Identity} of the user acessing files
   * @return {@link DocumentGroupsSize}
   * @throws ObjectNotFoundException when parentFolderId doesn't exisits
   */

  DocumentGroupsSize getGroupDocumentsCount(DocumentTimelineFilter filter, Identity aclIdentity) throws ObjectNotFoundException;

  /**
   * Retrieves a list of accessible files, for a selected user, by applying the
   * designated filter. The parentForlderId of filter object will be used to
   * select the list of Nodes to retrieve. Without a search criterion, the
   * folder's children are listed folders first, then files, a shortcut with
   * its target's kind, and a shortcut whose target is gone or can't be read
   * left out; the offset and the limit page over that listing, so successive
   * pages neither skip nor repeat a child.
   * 
   * @param filter {@link DocumentFolderFilter} that contains filtering criteria
   * @param offset Offset of the result list
   * @param limit Limit of the result list; without a search criterion, none
   *          when zero or less
   * @param aclIdentity {@link Identity} of the user acessing files
   * @return {@link List} of {@link AbstractNode}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when parentFolderId doesn't exisits
   */
  List<AbstractNode> getFolderChildNodes(DocumentFolderFilter filter,
                                         Identity aclIdentity,
                                         int offset,
                                         int limit) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Retrieves breadcrumb of the given node.
   *
   * @param folderId Id of the given folder
   * @param aclIdentity {@link Identity} of the user acessing files
   * @return {@link List} of {@link AbstractNode}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   */
  List<BreadCrumbItem> getBreadcrumb(long ownerId, String folderId, String folderPath, Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Retrieves breadcrumb of the given node.
   *
   * @param folderId Id of the given folder
   * @param aclIdentity {@link Identity} of the user acessing files
   * @param withChildren get all children
   * @return {@link List} of {@link AbstractNode}
   */
  List<FullTreeItem> getFullTreeData(long ownerId, String folderId, String destinationFolderPath, Identity aclIdentity, boolean withChildren, boolean showHidden) throws IllegalAccessException, ObjectNotFoundException;


  /**
   * Set the document visibility.
   *
   * @param ownerId the owner Id
   * @param documentID theDocument Id
   * @param hidden the hidden value
   * @param aclIdentity user Identuty
   */
    void setDocumentVisibility(long ownerId, String documentID, Boolean hidden, Identity aclIdentity) throws ObjectAlreadyExistsException;

    /**
   * Duplicate the given node.
   *
   * @param fileId Id of the given file
   * @param aclIdentity {@link Identity} of the user acessing files
   * @return  {@link AbstractNode}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   */
  AbstractNode duplicateDocument(long ownerId, String fileId, String prefixClone, Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Copy the given node.
   *
   * @param nodeId           Id of the given file
   * @param destintionNodeId Id of the destination folder
   * @param aclIdentity      {@link Identity} of the user acessing files
   * @return {@link AbstractNode}
   * @throws ObjectNotFoundException when folderId doesn't exisits
   */
  public AbstractNode copyDocument(String nodeId, String destintionNodeId, Identity aclIdentity) throws ObjectNotFoundException;

  /**
   * Move the given node.
   *
   * @param session current jcr session
   * @param fileId Id of the given file
   * @param aclIdentity {@link Identity} of the user acessing files
   * @param conflictAction conflict action
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   * @throws ObjectAlreadyExistsException when file with same name already exists
   *           in the target path
   */
  void moveDocument(Session session,
                    long ownerId,
                    String fileId,
                    String destPath,
                    Identity aclIdentity,
                    String conflictAction) throws Exception;

  /**
   * Move the given node.
   *
   * @param fileId Id of the given file
   * @param aclIdentity {@link Identity} of the user acessing files
   * @param conflictAction conflict action
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   * @throws ObjectAlreadyExistsException when file with same name already exists
   *           in the target path
   */
  void moveDocument(long ownerId, String fileId, String destPath, Identity aclIdentity, String conflictAction) throws IllegalAccessException, ObjectNotFoundException, ObjectAlreadyExistsException;

  AbstractNode createFolder(long ownerId, String folderId, String folderPath, String title, Identity aclIdentity) throws IllegalAccessException,  ObjectAlreadyExistsException,
                                                                               ObjectNotFoundException;

  /**
   * Creates a new empty office document (Word/Excel/PowerPoint/ODF) from the
   * platform's blank template, by delegating to the ecms {@code DocumentService}.
   *
   * @param ownerId owner identity id (used when folderId is blank to resolve the
   *          identity root folder)
   * @param folderId identifier of the parent folder node
   * @param folderPath optional relative path under the parent folder
   * @param title document title (should already include the file extension)
   * @param documentType office document extension (e.g. docx, xlsx, pptx, odt,
   *          ods, odp) matched against the installed template providers
   * @param aclIdentity ACL identity of the user creating the document
   * @return the created {@link AbstractNode}
   * @throws IllegalAccessException when the user isn't allowed to edit the parent
   *           folder
   * @throws ObjectNotFoundException when the parent folder or a matching template
   *           doesn't exist
   * @throws ObjectAlreadyExistsException when a document with the same name
   *           already exists in the target folder
   */
  AbstractNode createDocumentFromTemplate(long ownerId, String folderId, String folderPath, String title, String documentType, Identity aclIdentity) throws IllegalAccessException,
                                                                               ObjectNotFoundException,
                                                                               ObjectAlreadyExistsException;

  /**
   * Checks a file's content with the platform's upload validators, as a new
   * file uploaded from a browser is checked.
   *
   * @param fileName the file name
   * @param mimeType the file's type
   * @param content the file's content
   * @throws IllegalArgumentException when a validator refuses the file, with
   *           its message
   */
  default void validateUploadedContent(String fileName, String mimeType, byte[] content) {
    throw new UnsupportedOperationException();
  }

  /**
   * Checks a file on disk with the platform's upload validators, as a new file
   * uploaded from a browser is checked, each validator reading it from a fresh
   * stream: the file is never read whole into memory.
   *
   * @param fileName the file name the validators check
   * @param mimeType the file's type
   * @param file the file's location on disk
   * @throws IllegalArgumentException when a validator refuses the file, with
   *           its message
   * @throws IllegalStateException when the file can't be read
   */
  default void validateUploadedFile(String fileName, String mimeType, Path file) {
    throw new UnsupportedOperationException();
  }

  /**
   * Checks an upload can be filed under a name in a folder with a conflict
   * action, as {@link #saveUploadedDocument} checks it when it files it.
   *
   * @param folderId the folder
   * @param name the file name, with its extension
   * @param conflictAction what to do when the folder holds a file of that name
   * @param aclIdentity the user
   * @throws IllegalArgumentException when the folder is not a folder, or a
   *           new version is asked of a document of that name that is not a
   *           file (a folder or a shortcut), or keeps no versions
   * @throws IllegalStateException when the folder can't be read
   * @throws ObjectNotFoundException when the folder doesn't exist
   */
  default void checkUploadConflict(String folderId,
                                   String name,
                                   UploadConflictAction conflictAction,
                                   Identity aclIdentity) throws ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Files an uploaded file into a folder the way the Documents app files an
   * upload, as the user: under the user's own session, with the same activity,
   * events, hidden state and name handling.
   *
   * @param folderId the folder the file is filed into
   * @param uploadId the upload, registered in the platform's upload service;
   *          it is consumed once the file is filed
   * @param name the file name, with its extension
   * @param conflictAction what to do when the folder holds a file of that name
   * @param aclIdentity the user filing it
   * @return the filed document
   * @throws IllegalArgumentException when the name is blank, the folder is
   *           not a folder, or a new version is asked of a document of that
   *           name that is not a file (a folder or a shortcut), or keeps no versions
   * @throws IllegalStateException when the file couldn't be filed, or read
   *           back once filed
   * @throws IllegalAccessException when the user can't add documents to the
   *           folder
   * @throws ObjectNotFoundException when the folder or the upload doesn't exist
   */
  default FileNode saveUploadedDocument(String folderId,
                                        String uploadId,
                                        String name,
                                        UploadConflictAction conflictAction,
                                        Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  String getNewName(long ownerId,
                    String folderId,
                    String folderPath,
                    String title) throws IllegalAccessException,
                                                 ObjectAlreadyExistsException,
                                                 ObjectNotFoundException;

  void renameDocument(long ownerId, String documentID, String title, Identity aclIdentity) throws IllegalAccessException,  ObjectAlreadyExistsException,
                                                                               ObjectNotFoundException;
  void updatePermissions(String documentID, NodePermission nodePermissionEntity, Identity aclIdentity);

  void downloadDocuments(int actionId, List<AbstractNode> documents, Identity identity, long authenticatedUserId);

  /**
   * Shares a document with given user or space
   *
   * @param documentId document id
   * @param destId target user or space identity id
   * @param aclIdentity current user Identity
   * @throws IllegalAccessException
   */
  void shareDocument(String documentId, long destId, Identity aclIdentity, boolean broadcast) throws IllegalAccessException;

  void unShareDocument(String documentId, long destId) throws RepositoryException;

  void notifyMember(String documentId, long destId) throws IllegalAccessException;

  boolean canAccess(String documentID, Identity aclIdentity);
  
  default void updateDocumentDescription(long ownerId,
                                         String documentID,
                                         String description,
                                         Identity aclIdentity) throws IllegalStateException, RepositoryException {
    throw new IllegalStateException("updateDocumentDescription not implemented in the target classs");
  }

  /**
   * Updates the Text transcription of a Document
   *
   * @param documentId Video or Audio UUID
   * @param transcription Media Audio Transcription Text
   * @param aclIdentity current user Identity
   * @throws IllegalAccessException when the current user can't edit the
   *           designated document
   */
  default void updateAudioTranscription(String documentId,
                                        String transcription,
                                        Identity aclIdentity) throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  /**
   * Updates the Text transcription of a Document
   *
   * @param documentId Video or Audio UUID
   * @param transcription Media Audio Transcription Text
   */
  default void updateAudioTranscription(String documentId,
                                        String transcription) {
    throw new UnsupportedOperationException();
  }

  /**
   * A previously saved media Text transcription content
   *
   * @param documentId Video or Audio UUID
   * @param aclIdentity current user Identity
   * @return the transcription text
   * @throws IllegalAccessException when the current user can't access the
   *           designated document
   */
  default String getAudioTranscription(String documentId, Identity aclIdentity) throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  /**
   * A previously saved media Text transcription content
   *
   * @param documentId Video or Audio UUID
   * @return the transcription text
   */
  default String getAudioTranscription(String documentId) {
    throw new UnsupportedOperationException();
  }

  /**
   * The transcription state of a media file, read with the rights of the given
   * user. A file transcribed before the state existed, which holds a transcript
   * but no state, reads as {@link TranscriptionStatus#DONE}; a file never sent
   * to transcription reads with a null status.
   *
   * @param documentId the file's UUID
   * @param aclIdentity the user reading it
   * @return the state, never null
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist, or the user
   *           cannot see it
   */
  default TranscriptionState getTranscriptionState(String documentId,
                                                   Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * The transcription state of a media file, read as the system.
   *
   * @param documentId the file's UUID
   * @return the state, never null
   * @throws ObjectNotFoundException when the file does not exist
   */
  default TranscriptionState getTranscriptionState(String documentId) throws ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Writes the transcription state of a media file as the system: every field
   * of the state is written, a null one removed, and the update time set to now.
   *
   * @param documentId the file's UUID
   * @param state the state to write
   * @throws ObjectNotFoundException when the file does not exist
   */
  default void updateTranscriptionState(String documentId, TranscriptionState state) throws ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Marks a media file QUEUED as the system, only when it has no transcription
   * state yet: the read and the write are made in one session, so a run's state is
   * never written over.
   *
   * @param documentId the file's UUID
   * @return true when the state was written, false when the file had one
   * @throws ObjectNotFoundException when the file does not exist, or the session
   *           creating it is not saved yet
   */
  default boolean markTranscriptionQueued(String documentId) throws ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Removes, as the system, the transcription state of a media file not
   * transcribed yet, so that the file reads as never transcribed and can be
   * queued again; a transcribed file, or one with no state, is left as it is.
   *
   * @param documentId the file's UUID
   * @return true when a state was removed
   * @throws ObjectNotFoundException when the file does not exist
   */
  default boolean clearTranscriptionState(String documentId) throws ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Writes, as the system and in one save, the transcript of a media file, its
   * timed segments and its final state.
   *
   * @param documentId the file's UUID
   * @param transcription the transcript's text
   * @param segments the timed segments as a JSON list, or null when there are
   *          none
   * @param state the state to write, {@link TranscriptionStatus#DONE}
   * @throws ObjectNotFoundException when the file does not exist
   */
  default void completeTranscription(String documentId,
                                     String transcription,
                                     String segments,
                                     TranscriptionState state) throws ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * The timed segments of a media file's transcript, read with the rights of
   * the given user.
   *
   * @param documentId the file's UUID
   * @param aclIdentity the user reading it
   * @return the segments as a JSON list, or null when there are none
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist, or the user
   *           cannot see it
   */
  default String getTranscriptionSegments(String documentId,
                                          Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * The timed segments of a media file's transcript, read as the system, for
   * the platform's own background work on a transcript.
   *
   * @param documentId the file's UUID
   * @return the segments as a JSON list, or null when there are none
   * @throws ObjectNotFoundException when the file does not exist
   */
  default String getTranscriptionSegments(String documentId) throws ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Writes, as the system, the chapters a media file's transcript is grouped
   * into (EXO-92122), only while the transcript they were made from is still
   * the stored one: done, and with the update time read before making them.
   *
   * @param documentId the file's UUID
   * @param chapters the chapters as a JSON list
   * @param transcriptUpdated the update time of the transcription state the
   *          chapters were made from, in milliseconds since the epoch
   * @return true when written, false when the transcript changed since, or is
   *         not done
   * @throws ObjectNotFoundException when the file does not exist
   */
  default boolean saveTranscriptionChapters(String documentId,
                                            String chapters,
                                            long transcriptUpdated) throws ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * The chapters a media file's transcript is grouped into, read with the
   * rights of the given user.
   *
   * @param documentId the file's UUID
   * @param aclIdentity the user reading it
   * @return the chapters as a JSON list, or null when there are none
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist, or the user
   *           cannot see it
   */
  default String getTranscriptionChapters(String documentId,
                                          Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * The person each named speaker of a media file's transcript is, read with
   * the rights of the given user (EXO-92123).
   *
   * @param documentId the file's UUID
   * @param aclIdentity the user reading it
   * @return who each named speaker is, by speaker number: an identity id, or a
   *         guest's name after {@link org.exoplatform.documents.model.TranscriptSpeakers#GUEST_PREFIX};
   *         empty when none is named
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist, or the user
   *           cannot see it
   */
  default Map<String, String> getTranscriptionSpeakers(String documentId,
                                                       Identity aclIdentity) throws IllegalAccessException,
                                                                             ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Says which person a speaker of a media file's transcript is, or that it is
   * nobody named any more, as a user who can edit the file (EXO-92123).
   *
   * @param documentId the file's UUID
   * @param speaker the speaker's number in the transcript
   * @param identityId who the speaker is: the identity id of the person, or a
   *          guest's name after
   *          {@link org.exoplatform.documents.model.TranscriptSpeakers#GUEST_PREFIX};
   *          null to unname the speaker. The value is stored as given: a
   *          guest's name is validated by
   *          {@code DocumentTranscriptionService#setGuestSpeaker}, which callers
   *          go through
   * @param aclIdentity the user naming it
   * @throws IllegalAccessException when the user may not edit the file
   * @throws ObjectNotFoundException when the file does not exist, or the user
   *           cannot see it
   */
  default void setTranscriptionSpeaker(String documentId,
                                       String speaker,
                                       String identityId,
                                       Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Who a file is for, read as the system: its owner, the users it is shared
   * with one by one, and the space whose documents hold it (EXO-92126).
   *
   * @param documentId the file's UUID
   * @return who the file is for
   * @throws ObjectNotFoundException when the file does not exist
   */
  default DocumentAudience getDocumentAudience(String documentId) throws ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Replaces, as the system, the proposals of who the speakers of a media
   * file's transcript are (EXO-92126), only while the transcript they were made
   * from is still the stored one; none removes them.
   *
   * @param documentId the file's UUID
   * @param suggestions the proposals, one per speaker at most
   * @param transcriptUpdated the update time of the transcription state the
   *          proposals were made from, in milliseconds since the epoch
   * @return true when written, false when the transcript changed since, or is
   *         not done
   * @throws ObjectNotFoundException when the file does not exist
   */
  default boolean saveTranscriptionSpeakerSuggestions(String documentId,
                                                      List<TranscriptionSpeakerSuggestion> suggestions,
                                                      long transcriptUpdated) throws ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * The proposals of who the speakers of a media file's transcript are, read
   * with the rights of the given user (EXO-92126).
   *
   * @param documentId the file's UUID
   * @param aclIdentity the user reading them
   * @return the proposals, in the order they were saved; empty when none
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist, or the user
   *           cannot see it
   */
  default List<TranscriptionSpeakerSuggestion> getTranscriptionSpeakerSuggestions(String documentId,
                                                                                  Identity aclIdentity) throws IllegalAccessException,
                                                                                                         ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Removes the proposal for one speaker of a media file's transcript, as a
   * user who can edit the file (EXO-92126).
   *
   * @param documentId the file's UUID
   * @param speaker the speaker's number in the transcript
   * @param aclIdentity the user removing it
   * @throws IllegalAccessException when the user may not edit the file
   * @throws ObjectNotFoundException when the file does not exist, or the user
   *           cannot see it
   */
  default void removeTranscriptionSpeakerSuggestion(String documentId,
                                                    String speaker,
                                                    Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Names a speaker of a media file's transcript after a proposal the platform
   * made, as a user who can edit the file, and marks that proposal applied, in
   * one save under the file's lock (EXO-92126). What the proposal rests on in
   * the file is read again at that moment: the transcript must still be the
   * one it was made from, the proposal must still be stored (not dismissed),
   * neither the speaker nor the person may be named, and a proposal made by
   * elimination needs its other voice still named after the person it was
   * taken to be. A name applied this way never replaces one a person gave, nor
   * names a voice of another transcript. Whether the person is still an
   * enabled user is not read here: the caller checks it just before, so a
   * person disabled in between is still named.
   *
   * @param documentId the file's UUID
   * @param suggestion the proposal
   * @param transcriptUpdated the update time of the transcription state the
   *          proposal was made from, in milliseconds since the epoch
   * @param aclIdentity the user naming it
   * @return true when the speaker was named; false when any of these no longer
   *         holds
   * @throws IllegalAccessException when the user may not edit the file
   * @throws ObjectNotFoundException when the file does not exist, or the user
   *           cannot see it
   */
  default boolean applyTranscriptionSpeakerSuggestion(String documentId,
                                                      TranscriptionSpeakerSuggestion suggestion,
                                                      long transcriptUpdated,
                                                      Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Creates a shortcut for a document
   *
   * @param documentId     document id
   * @param destPath       destination path
   * @param aclIdentity    user identity id
   * @param conflictAction conflict action
   * @throws IllegalAccessException
   * @throws ObjectAlreadyExistsException
   */
  void createShortcut(String documentId, String destPath, String aclIdentity, String conflictAction) throws IllegalAccessException, ObjectAlreadyExistsException;

  /**
   * Retrieves versions of specific file
   *
   * @param fileNodeId target file node id
   * @param aclIdentity user identity id
   * @return {@link List} of {@link FileVersion}
   */
  List<FileVersion> getFileVersions(String fileNodeId, String aclIdentity);

  /**
   * Retrieves a document By Id
   *
   * @param documentId target file node id
   * @param aclIdentity user identity id
   * @return {@link AbstractNode}
   * @throws ObjectNotFoundException when document doesn't exists
   * @throws IllegalAccessException when user can't access document
   */
  AbstractNode getDocumentById(String documentId, String aclIdentity) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * @param documentId target file node id
   * @return {@link AbstractNode}
   */
  AbstractNode getDocumentById(String documentId);

  /**
   * Retrieves the path of a document as reached from the drive of an owner:
   * the document's own path when it lies in that drive, otherwise the path
   * through a shortcut of that drive targeting the document or one of its
   * ancestors; in a user drive, only the document or its parent folder.
   *
   * @param documentId target document node id
   * @param ownerId identity id of the drive owner (space or user)
   * @param aclIdentity user identity
   * @return the path of the document from the owner's drive, null when no
   *         shortcut of that drive leads to it
   * @throws ObjectNotFoundException when the document or the owner's drive
   *           isn't found or isn't accessible to the user
   */
  default String getDocumentPathInDrive(String documentId, long ownerId, Identity aclIdentity) throws ObjectNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Tells which space drive holds a node itself, read as the system. A
   * shortcut or a share link ({@code exo:symlink}) isn't the node it points
   * to: it holds nothing of its own, so it belongs to no space here.
   *
   * @param documentId the node id
   * @return the group id ({@code /spaces/<name>}) of the space whose
   *         {@code Documents} drive holds the node, or null when the node is
   *         missing, is a shortcut or a share link, lies outside a space's
   *         {@code Documents} drive (a personal drive, the trash, any other
   *         place), or can't be read
   */
  String getDocumentSpaceGroupId(String documentId);

  /**
   * Retrieves a document content By its Id
   *
   * @param documentId  target file node id
   * @param aclIdentity user identity id
   * @return {@link FileContent}
   */
  FileContent getDocumentContent(String documentId, String aclIdentity) throws ObjectNotFoundException;

  /**
   * update or add a version summary
   *
   * @param originFileId original file id
   * @param versionId version id
   * @param summary new summary to be saved
   * @param aclIdentity current user identity
   * @return {@link FileVersion}
   */
  FileVersion updateVersionSummary(String originFileId, String versionId, String summary, String aclIdentity);

  /**
   * restore document version
   *
   * @param versionId   version id
   * @param aclIdentity current user identity
   * @return {@link FileVersion}
   */
  FileVersion restoreVersion(String versionId, String aclIdentity);

  /**
   * Get the zip for download by action ID
   *
   * @param actionId action id
   * @param userName current user name
   */
  byte[] getDownloadZipBytes(int actionId, String userName) throws IOException;

  /**
   * Cancel any bulk action by action ID
   *
   * @param actionId action id
   * @param userName current user name
   */
  void cancelBulkAction(String actionId, String userName) throws IOException;

  /**
   * Creates a new version from an input stream
   *
   * @param nodeId target node id
   * @param aclIdentity current user identity id
   * @param newContent the new content to be set in the new version
   * @return {@link FileVersion}
   */
  FileVersion createNewVersion(String nodeId, String aclIdentity, InputStream newContent);

  /**
   * Move list of documents in bulk
   *
   * @param actionId       action id
   * @param ownerId        owner id
   * @param documents      list of documents to move
   * @param destPath       destination path
   * @param userIdentityId current user acl identity id
   * @param identityId     current user identity id
   */
  void moveDocuments(int actionId, long ownerId, List<AbstractNode> documents, String destPath, Identity userIdentityId, long identityId);

  /**
   * Checks if user has edit permission on document
   *
   * @param nodeId document node id
   * @param aclUserIdentity user identity id
   * @return true if has edit permission or false
   */
  boolean hasEditPermissions(String nodeId, Identity aclUserIdentity);

  /**
   * Gets a download item of a given document
   *
   * @param documentId document id
   * @return {@link DownloadItem}
   */
  DownloadItem getDocumentDownloadItem(String documentId);

  /**
   * Download a zipped folder
   *
   * @param folderId  folder node id
   * @return downloaded zip path
   */
  String downloadFolder(String folderId);

  /**
   * Returns a document file content as Text
   *
   * @param documentId Document identifier
   * @return the text representation of a file
   */
  default String getFileContentAsText(String documentId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the text of a document file, from the search index when it already
   * holds it, else extracted from the file itself, and when there is none, why.
   * No permission is checked here: the caller checks the user can access the
   * document first.
   *
   * @param documentId Document identifier
   * @return the text of the file and where it came from, or why there is none
   */
  default DocumentTextContent getFileTextContent(String documentId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns a part of the text of a document file, with the length of the
   * whole text and the document's outline, so a document too large to be read
   * in one go is read part by part, or one section at a time. The whole text
   * is extracted from the file once per version of it and kept for the next
   * parts. No permission is checked here: the caller checks the user can
   * access the document first.
   *
   * @param documentId Document identifier
   * @param offset the first character to read
   * @param maxChars the most characters to read, 0 for the outline only
   * @return the part read, or why there is no text
   */
  default DocumentTextPage getFileTextPage(String documentId, long offset, int maxChars) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns a document file content as Text
   * 
   * @param documentId Document identifier
   * @param aclIdentity {@link Identity} of the user acessing file
   * @return the {@link InputStream} of the binary
   * @throws IllegalAccessException when user can't access document
   */
  default InputStream getFileContentAsStream(String documentId, Identity aclIdentity) throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  /**
   * Import list of documents from an uploaded zip
   *
   * @param ownerId owner id
   * @param space space where to create documents
   * @param folderId folder id
   * @param folderPath folder path
   * @param importId import id
   * @param conflict conflict rule
   * @param identity current user identity
   * @param authenticatedUserId current user identity id
   */
  void importFiles(String importId,
                   Space space,
                   String userName,
                   String folderId,
                   String folderPath,
                   String conflict,
                   Identity identity,
                   String ownerId,
                   long authenticatedUserId) throws Exception;

  boolean canImport(Identity identity);

  List<String> getFavoriteFileIds(DocumentNodeFilter filter,
                                  Identity aclUserIdentity,
                                  int offset,
                                  int limit);

  /**
   * @param spaceIdentityId Space {@link org.exoplatform.social.core.identity.model.Identity} Identifier
   * @return {@link List} of Category Ids used in Documents
   */
  default List<Long> getDocumentCategoryIds(long spaceIdentityId, Identity aclIdentity) {
    throw new UnsupportedOperationException();
  }

  void clearSymlinksNavHistory();

}
