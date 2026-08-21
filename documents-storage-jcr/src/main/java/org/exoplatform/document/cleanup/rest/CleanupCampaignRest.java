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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.rest;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.rest.model.CampaignComparisonRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignFailureGroupRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignItemRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignScanUnitProgressRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignRestEntity;
import org.exoplatform.document.cleanup.rest.model.MyItemsSummaryRestEntity;
import org.exoplatform.document.cleanup.rest.model.PagedResult;
import org.exoplatform.document.cleanup.rest.model.UpdateCampaignRestEntity;
import org.exoplatform.document.cleanup.rest.util.CleanupEntityBuilder;
import org.exoplatform.document.cleanup.service.CleanupCampaignService;
import org.exoplatform.document.cleanup.util.CleanupConstants;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/cleanup/campaigns")
public class CleanupCampaignRest {

  /**
   * Sortable keys, all of them item-entity attributes but ONE: the computed
   * {@link CleanupConstants#RECLAIMABLE_SORT_KEY}, which the Storage layer
   * translates into an ORDER BY over the reclaimable expression itself. An
   * unknown key still gets a 400 — this set stays the whole contract.
   */
  private static final Set<String> SORTABLE_ITEM_FIELDS  = Set.of("id",
                                                                  "path",
                                                                  "ownerIdentityId",
                                                                  "fileSize",
                                                                  "versionsSize",
                                                                  "lastModifiedDate",
                                                                  "state",
                                                                  "action",
                                                                  "reclaimedBytes",
                                                                  CleanupConstants.RECLAIMABLE_SORT_KEY);

  /**
   * Sortable fields that are already unique per item row, hence already yield a
   * TOTAL order and need no tiebreaker appended.
   * <p>
   * Only the primary key qualifies. {@code path} does NOT: the sole uniqueness
   * the item table enforces is {@code UK_DOC_CLEANUP_ITEM_NODE (CAMPAIGN_ID,
   * NODE_UUID)}, and PATH is a nullable column — a file deleted and recreated at
   * the same path across a resumed scan yields two rows sharing a path, at which
   * point an ordering ending on the path stops being total.
   */
  private static final Set<String> UNIQUE_ITEM_FIELDS    = Set.of("id");

  private static final String      TIEBREAKER_FIELD      = "id";

  /**
   * Default ordering of the ADMIN items table: its size column shows the item's
   * CONTENT size (and its minSize filter narrows on that same column), so
   * ranking it by {@code fileSize} is ranking it by what it displays.
   */
  private static final String      DEFAULT_ITEM_SORT     = "fileSize";

  /**
   * Default ordering of the USER review list: what each row actually FREES, not
   * its content size alone. The two diverge for a DELETE — which reclaims its
   * content PLUS the version history the delete destroys — so a 1 MB file
   * carrying 500 MB of history displayed 501 MB while sorting as 1 MB, and the
   * row freeing the most could sit at the bottom of a list the UI labels sorted
   * by size. That ordering exists for TRIAGE: a user holding ten thousand
   * candidates keeps what matters by starting at the top of it.
   */
  private static final String      DEFAULT_MY_ITEMS_SORT = CleanupConstants.RECLAIMABLE_SORT_KEY;

  @Autowired
  private CleanupCampaignService   campaignService;

  @Autowired
  private IdentityManager          identityManager;

  @Secured("administrators")
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "GET", summary = "Retrieve the list of cleanup campaigns", description = "Retrieve the list of cleanup campaigns with their item aggregates")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
  })
  public List<CampaignRestEntity> getCampaigns() {
    return campaignService.getCampaigns().stream().map(CleanupEntityBuilder::build).toList();
  }

  @Secured("administrators")
  @GetMapping(path = "defaults", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "GET", summary = "Retrieve the default campaign parameters", description = "Retrieve the platform default campaign parameters, to pre-fill the creation form")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
  })
  public CleanupParams getDefaults() {
    return campaignService.getDefaultParams();
  }

  /**
   * ASYNCHRONOUS: the campaign row is created synchronously, its dry-run scan
   * is handed off to a worker thread — hence the 202. The returned campaign
   * carries no scan result yet; the scan progress and the SIMULATED completion
   * are pushed on the {@code /eXo/Application/CleanupCampaign} CometD channel
   * ({@code campaign.progress} / {@code campaign.stateChanged} events).
   */
  @Secured("administrators")
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(method = "POST", summary = "Create a cleanup campaign and launch its dry-run", description = "Create a cleanup campaign, snapshotting parameters, then launch its dry-run scan asynchronously. Returns 202: the scan progress is followed on the CometD channel /eXo/Application/CleanupCampaign")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "202", description = "Dry-run scan accepted, progress followed on the CometD channel"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
  })
  public CampaignRestEntity createCampaign(
                                           @io.swagger.v3.oas.annotations.parameters.RequestBody
                                           @RequestBody
                                           CampaignRestEntity campaignEntity) {
    try {
      return CleanupEntityBuilder.build(campaignService.createCampaign(campaignEntity.getName(),
                                                                       CleanupEntityBuilder.toParamOverrides(campaignEntity)));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @Secured("administrators")
  @GetMapping(path = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "GET", summary = "Retrieve a cleanup campaign", description = "Retrieve a cleanup campaign with its progress and item aggregates")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public CampaignRestEntity getCampaign(
                                        @Parameter(description = "Campaign identifier", required = true)
                                        @PathVariable("id")
                                        long id) {
    try {
      return CleanupEntityBuilder.build(campaignService.getCampaign(id));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  /**
   * PATCH on the campaign resource rather than an {@code {id}/rename} or
   * {@code {id}/grace} sub-resource: this updates ATTRIBUTES of the campaign, it
   * triggers no action and no lifecycle transition — unlike the {@code publish} /
   * {@code execute} sub-resources next to it, which do. ONE endpoint for the
   * whole editable set, each field applied only when the body carries it: a
   * second endpoint per attribute would multiply round-trips for a form the
   * console submits at once. Answers the updated campaign so the console
   * re-renders its list and its header from the response instead of refetching.
   */
  @Secured("administrators")
  @PatchMapping(path = "{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "PATCH", summary = "Update a cleanup campaign", description = "Partially update the editable attributes of a cleanup campaign — its name and its grace period — each one applied only when the body carries it. The name is pure metadata and is editable in any state, a completed or cancelled campaign included; the grace period is state-guarded and only editable while the campaign is DRAFT, SIMULATED or PUBLISHED, and editing it on a PUBLISHED campaign moves its grace deadline — which may then only be EXTENDED, never shortened, a deadline having been promised to the owners of the candidate files")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public CampaignRestEntity updateCampaign(
                                           @Parameter(description = "Campaign identifier", required = true)
                                           @PathVariable("id")
                                           long id,
                                           @io.swagger.v3.oas.annotations.parameters.RequestBody
                                           @RequestBody
                                           UpdateCampaignRestEntity updateEntity) {
    try {
      // Both fields are passed through verbatim: trimming them, validating them
      // and deciding which state may edit which is the Service's business, this
      // layer holds none
      return CleanupEntityBuilder.build(campaignService.updateCampaign(id, updateEntity.getName(), updateEntity.getGraceDays()));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  // CANCEL is a POST on its own path and DELETE really deletes. They were the same
  // verb until a delete existed, and "DELETE cancels" is the kind of shape that
  // reads fine to whoever wrote it and destroys a campaign for whoever did not.
  @Secured("administrators")
  @PostMapping("{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(method = "POST", summary = "Cancel a cleanup campaign", description = "Cancel a cleanup campaign from any non-terminal state. The campaign is kept, with everything it had already found")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "204", description = "Request fulfilled"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public void cancelCampaign(
                             @Parameter(description = "Campaign identifier", required = true)
                             @PathVariable("id")
                             long id) {
    try {
      campaignService.cancelCampaign(id);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * 204 and not 202, deliberately, although the report rows are dropped
   * asynchronously (see {@code CleanupCampaignService#deleteCampaign}): the
   * campaign row is gone before this answers, so the resource really IS deleted
   * and nothing about it is still pending. What continues in the background is
   * the collection of rows no query can reach any more — the reason it is not
   * done here being that a simulated campaign's report holds hundreds of
   * thousands of them, and no REST call may depend on work whose duration grows
   * with the corpus.
   */
  @Secured("administrators")
  @DeleteMapping("{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(method = "DELETE", summary = "Delete a cleanup campaign", description = "Delete a DRAFT, SIMULATED or CANCELLED campaign with its report, its scan units and its archive. Answers as soon as the campaign is unreachable, its report rows being dropped in the background. A COMPLETED campaign is never deletable — it records an irreversible purge — and a running or published one must be cancelled first. The users' keep decisions are NOT removed")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "204", description = "Request fulfilled"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public void deleteCampaign(
                             @Parameter(description = "Campaign identifier", required = true)
                             @PathVariable("id")
                             long id) {
    try {
      campaignService.deleteCampaign(id);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @Secured("administrators")
  @PostMapping(path = "{id}/publish", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "POST", summary = "Publish a simulated cleanup campaign", description = "Publish a SIMULATED campaign, starting its grace period. A single campaign can be active platform-wide")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public CampaignRestEntity publishCampaign(
                                            @Parameter(description = "Campaign identifier", required = true)
                                            @PathVariable("id")
                                            long id) {
    try {
      return CleanupEntityBuilder.build(campaignService.publishCampaign(id));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * ASYNCHRONOUS: the campaign is switched to EXECUTING synchronously, the
   * batched purge itself is handed off to a worker thread — hence the 202. The
   * purge progress and the COMPLETED transition are pushed on the
   * {@code /eXo/Application/CleanupCampaign} CometD channel
   * ({@code campaign.progress} / {@code campaign.stateChanged} events).
   */
  @Secured("administrators")
  @PostMapping(path = "{id}/execute", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(method = "POST", summary = "Execute a locked cleanup campaign", description = "Trigger the batched purge of a LOCKED campaign asynchronously. Returns 202: the purge progress is followed on the CometD channel /eXo/Application/CleanupCampaign")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "202", description = "Purge accepted, progress followed on the CometD channel"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public CampaignRestEntity executeCampaign(
                                            @Parameter(description = "Campaign identifier", required = true)
                                            @PathVariable("id")
                                            long id) {
    try {
      return CleanupEntityBuilder.build(campaignService.executeCampaign(id));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * A sub-resource POST, unlike the rename PATCH next to it: a retry is an
   * ACTION with a lifecycle transition behind it (COMPLETED back to EXECUTING),
   * not the update of an attribute.
   * <p>
   * ASYNCHRONOUS like {@code execute}: the failed items are requeued and the
   * campaign switched to EXECUTING synchronously, the purge itself is handed off
   * to a worker thread — hence the 202. Progress is pushed on the
   * {@code /eXo/Application/CleanupCampaign} CometD channel.
   */
  @Secured("administrators")
  @PostMapping(path = "{id}/retry", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(method = "POST", summary = "Retry the failed items of a completed cleanup campaign", description = "Requeue the retryable failed items of a COMPLETED campaign and re-execute it asynchronously — no new scan, no new grace period. Returns 202: the purge progress is followed on the CometD channel /eXo/Application/CleanupCampaign")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "202", description = "Retry accepted, progress followed on the CometD channel"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public CampaignRestEntity retryCampaign(
                                          @Parameter(description = "Campaign identifier", required = true)
                                          @PathVariable("id")
                                          long id) {
    try {
      return CleanupEntityBuilder.build(campaignService.retryCampaign(id));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Grouped failures of a campaign, so the console can show WHY items were
   * skipped and which of those failures a retry would re-attempt — the
   * {@code retryable} flag is the SERVER's answer, the client never decides it.
   * <p>
   * Answers an EMPTY list when the campaign has no failed item, and also once the
   * retention job archived and purged its item rows: the groups are computed over
   * those rows and are not part of the summary snapshotted at completion.
   */
  @Secured("administrators")
  @GetMapping(path = "{id}/failures", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "GET", summary = "Retrieve the grouped failures of a cleanup campaign", description = "Per-reason counts of a campaign's skipped items, each flagged retryable or not. Empty once the retention job purged the item rows")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public List<CampaignFailureGroupRestEntity> getCampaignFailures(
                                                                 @Parameter(description = "Campaign identifier", required = true)
                                                                 @PathVariable("id")
                                                                 long id) {
    try {
      return campaignService.getCampaignFailures(id).stream().map(CleanupEntityBuilder::build).toList();
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  /**
   * Grouped failures of a campaign's dry-run SCAN, so the console can say that
   * the report the administrator is about to publish does NOT cover the whole
   * tree, and WHY — one entry per subtree failure reason, with how many subtrees
   * carry it.
   * <p>
   * Answers an EMPTY list for every campaign whose scan covered the whole tree,
   * which is the normal case: only a scan the worker RECORDED as incomplete —
   * subtrees that failed every walk attempt they had — has anything to report
   * here. A subtree that failed while attempts remain is not one of them: the
   * campaign is still DRY_RUN_RUNNING and the watchdog is re-walking it.
   */
  @Secured("administrators")
  @GetMapping(path = "{id}/scan-units", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "GET", summary = "Retrieve the per-unit progress of a cleanup campaign dry run", description = "Subtree state counts, deepest walk attempt spent, and the subtrees in flight. Readable WHILE the scan runs: it is what tells a resuming scan from a stuck one, which the node percentage cannot")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public CampaignScanUnitProgressRestEntity getCampaignScanUnits(
                                                                 @Parameter(description = "Campaign identifier", required = true)
                                                                 @PathVariable("id")
                                                                 long id) {
    try {
      return CleanupEntityBuilder.build(campaignService.getCampaignScanUnitProgress(id));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @Secured("administrators")
  @GetMapping(path = "{id}/scan-failures", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "GET", summary = "Retrieve the grouped scan failures of a cleanup campaign", description = "Per-reason counts of the subtrees a campaign's dry run could not walk. Empty unless the scan was recorded incomplete")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public List<CampaignFailureGroupRestEntity> getCampaignScanFailures(
                                                                     @Parameter(description = "Campaign identifier", required = true)
                                                                     @PathVariable("id")
                                                                     long id) {
    try {
      return campaignService.getCampaignScanFailures(id).stream().map(CleanupEntityBuilder::build).toList();
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @Secured("administrators")
  @GetMapping(path = "{id}/items", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "GET", summary = "Retrieve the items of a cleanup campaign", description = "Retrieve the items of a cleanup campaign, with optional owner/state/action/size filters and an optional path search, paged and sorted")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public PagedResult<CampaignItemRestEntity> getCampaignItems(// NOSONAR
                                                              @Parameter(description = "Campaign identifier", required = true)
                                                              @PathVariable("id")
                                                              long id,
                                                              @Parameter(description = "Owner identity id filter")
                                                              @RequestParam(name = "ownerIdentityId", required = false)
                                                              Long ownerIdentityId,
                                                              @Parameter(description = "Item state filter")
                                                              @RequestParam(name = "state", required = false)
                                                              String state,
                                                              @Parameter(description = "Action filter")
                                                              @RequestParam(name = "action", required = false)
                                                              String action,
                                                              @Parameter(description = "Minimal content size filter, in bytes")
                                                              @RequestParam(name = "minSize", required = false)
                                                              Long minSize,
                                                              @Parameter(description = "Case-insensitive search on the item path, which covers the file name and its folders alike. Blank or absent means no filtering")
                                                              @RequestParam(name = "search", required = false)
                                                              String search,
                                                              @Parameter(description = "Page index")
                                                              @RequestParam(name = "page", required = false, defaultValue = "0")
                                                              int page,
                                                              @Parameter(description = "Page size")
                                                              @RequestParam(name = "size", required = false, defaultValue = "20")
                                                              int size,
                                                              @Parameter(description = "Sort, as 'field,asc|desc'")
                                                              @RequestParam(name = "sort", required = false)
                                                              String sort) {
    try {
      Pageable pageable = PageRequest.of(page, size, parseSort(sort, DEFAULT_ITEM_SORT));
      return CleanupEntityBuilder.build(campaignService.getCampaignItems(id,
                                                                         ownerIdentityId,
                                                                         parseEnum(CleanupItemState.class, state),
                                                                         parseEnum(CleanupAction.class, action),
                                                                         minSize,
                                                                         search,
                                                                         pageable),
                                        identityManager,
                                        // ADMINISTRATORS-only endpoint: the
                                        // failure detail may name nodes outside a
                                        // given user's visibility, so it is
                                        // serialized HERE and nowhere else — see
                                        // the builder's javadoc
                                        true);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @Secured("administrators")
  @GetMapping(path = "{id}/compare/{otherId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "GET", summary = "Compare two cleanup campaigns", description = "Delta between two campaigns' candidate sets, matched by node uuid")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public CampaignComparisonRestEntity compareCampaigns(
                                                       @Parameter(description = "Base campaign identifier", required = true)
                                                       @PathVariable("id")
                                                       long id,
                                                       @Parameter(description = "Compared campaign identifier", required = true)
                                                       @PathVariable("otherId")
                                                       long otherId) {
    try {
      return CleanupEntityBuilder.build(campaignService.compareCampaigns(id, otherId));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  /**
   * STREAMED: the report is written page by page to the response body, so the
   * download starts immediately and a large campaign never materializes its CSV
   * in memory. The availability check runs BEFORE the streaming starts, since
   * the HTTP status is committed as soon as the first byte flows.
   */
  @Secured("administrators")
  @GetMapping(path = "{id}/archive")
  @Operation(method = "GET", summary = "Download the CSV report of a cleanup campaign", description = "Download the CSV report of a campaign, streamed page by page: generated live while item detail is retained, from the archive afterwards")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public ResponseEntity<StreamingResponseBody> getCampaignArchive(
                                                                  @Parameter(description = "Campaign identifier", required = true)
                                                                  @PathVariable("id")
                                                                  long id) {
    try {
      campaignService.checkArchiveAvailable(id);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
    StreamingResponseBody body = outputStream -> campaignService.writeArchiveCsv(id, outputStream);
    return ResponseEntity.ok()
                         .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cleanup-campaign-" + id + ".csv\"")
                         .contentType(MediaType.parseMediaType("text/csv"))
                         .body(body);
  }

  @Secured("users")
  @GetMapping(path = "published/my-items", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "GET", summary = "Retrieve the current user's cleanup candidates", description = "Retrieve the currently relevant campaign's items owned by the user (own files and managed-space files), with an optional path search, paged and sorted — by reclaimable size descending by default")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public PagedResult<CampaignItemRestEntity> getMyItems(HttpServletRequest request,
                                                        @Parameter(description = "Case-insensitive search on the item path, which covers the file name and its folders alike. Blank or absent means no filtering")
                                                        @RequestParam(name = "search", required = false)
                                                        String search,
                                                        @Parameter(description = "Page index")
                                                        @RequestParam(name = "page", required = false, defaultValue = "0")
                                                        int page,
                                                        @Parameter(description = "Page size")
                                                        @RequestParam(name = "size", required = false, defaultValue = "20")
                                                        int size,
                                                        @Parameter(description = "Sort, as 'field,asc|desc'")
                                                        @RequestParam(name = "sort", required = false)
                                                        String sort) {
    try {
      // Same allowlist and same stable tiebreaker as the admin items endpoint (the
      // review table is server-sorted too), but its OWN default: this list ranks
      // rows by what each one frees, see DEFAULT_MY_ITEMS_SORT
      Pageable pageable = PageRequest.of(page, size, parseSort(sort, DEFAULT_MY_ITEMS_SORT));
      // The failure detail is EXPLICITLY withheld here: this endpoint is
      // @Secured("users"), and a stack trace can name a referencing node in a
      // space the caller is not a member of. The bare failureReason code stays
      return CleanupEntityBuilder.build(campaignService.getMyItems(request.getRemoteUser(), search, pageable),
                                        identityManager,
                                        false);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @Secured("users")
  @GetMapping(path = "published/my-items/summary", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "GET", summary = "Retrieve the current user's cleanup summary", description = "Retrieve the per-user summary of the currently relevant campaign, with the personal outcome once completed")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public MyItemsSummaryRestEntity getMyItemsSummary(HttpServletRequest request) {
    try {
      return CleanupEntityBuilder.build(campaignService.getMyItemsSummary(request.getRemoteUser()));
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  /**
   * Parses a 'field,asc|desc' sort against {@link #SORTABLE_ITEM_FIELDS} —
   * defaulting, DESCENDING, to the field the calling endpoint opens on
   * ({@link #DEFAULT_ITEM_SORT} for the admin table,
   * {@link #DEFAULT_MY_ITEMS_SORT} for the user review list, which rank on
   * different figures because they DISPLAY different ones) — then makes it total
   * with {@link #withTiebreaker(Sort)}.
   */
  private Sort parseSort(String sort, String defaultField) {
    if (StringUtils.isBlank(sort)) {
      return withTiebreaker(Sort.by(Sort.Direction.DESC, defaultField));
    }
    String[] parts = sort.split(",");
    String field = parts[0].trim();
    if (!SORTABLE_ITEM_FIELDS.contains(field)) {
      throw new IllegalArgumentException("cleanup.invalidSortField");
    }
    Sort.Direction direction = parts.length > 1 && StringUtils.equalsIgnoreCase(parts[1].trim(), "asc") ? Sort.Direction.ASC :
                                                                                                        Sort.Direction.DESC;
    return withTiebreaker(Sort.by(direction, field));
  }

  /**
   * Appends {@code id ASC} as the LAST key of every ordering, default or
   * client-requested.
   * <p>
   * {@code fileSize} — like the computed reclaimable key, {@code state},
   * {@code action}, {@code path} and {@code ownerIdentityId} — is NOT unique, so
   * an offset-paged query over a block of ties has no total order: the database
   * is free to return the same row on two pages and to never return another one.
   * On a review table that means a user could page through their whole list and
   * never see a file that is about to be deleted. {@code id} is the primary key, so appending it makes
   * ANY ordering total — and, unlike the path, it depends on no invariant the
   * schema does not enforce (see {@link #UNIQUE_ITEM_FIELDS}).
   * <p>
   * Skipped when the requested field already IS {@code id}: a second key would
   * be dead weight in the ORDER BY.
   */
  private Sort withTiebreaker(Sort sort) {
    boolean alreadyTotal = sort.stream().map(Sort.Order::getProperty).anyMatch(UNIQUE_ITEM_FIELDS::contains);
    return alreadyTotal ? sort : sort.and(Sort.by(Sort.Direction.ASC, TIEBREAKER_FIELD));
  }

  private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    try {
      return Enum.valueOf(enumClass, value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("cleanup.invalidFilterValue");
    }
  }

}
