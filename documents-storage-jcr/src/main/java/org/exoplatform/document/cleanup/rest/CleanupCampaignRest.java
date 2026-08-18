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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.rest.model.CampaignComparisonRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignItemRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignRestEntity;
import org.exoplatform.document.cleanup.rest.model.MyItemsSummaryRestEntity;
import org.exoplatform.document.cleanup.rest.model.PagedResult;
import org.exoplatform.document.cleanup.rest.util.CleanupEntityBuilder;
import org.exoplatform.document.cleanup.service.CleanupCampaignService;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/cleanup/campaigns")
public class CleanupCampaignRest {

  private static final Set<String> SORTABLE_ITEM_FIELDS = Set.of("id",
                                                                 "path",
                                                                 "ownerIdentityId",
                                                                 "fileSize",
                                                                 "versionsSize",
                                                                 "state",
                                                                 "action",
                                                                 "reclaimedBytes");

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

  @Secured("administrators")
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "POST", summary = "Create a cleanup campaign and launch its dry-run", description = "Create a cleanup campaign, snapshotting parameters, then launch its dry-run scan")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
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

  @Secured("administrators")
  @DeleteMapping("{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(method = "DELETE", summary = "Cancel a cleanup campaign", description = "Cancel a cleanup campaign from any non-terminal state")
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

  @Secured("administrators")
  @PostMapping(path = "{id}/execute", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "POST", summary = "Execute a locked cleanup campaign", description = "Trigger the batched purge of a LOCKED campaign")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
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

  @Secured("administrators")
  @GetMapping(path = "{id}/items", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "GET", summary = "Retrieve the items of a cleanup campaign", description = "Retrieve the items of a cleanup campaign, with optional owner/state/action/size filters, paged")
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
      Pageable pageable = PageRequest.of(page, size, parseSort(sort));
      return CleanupEntityBuilder.build(campaignService.getCampaignItems(id,
                                                                         ownerIdentityId,
                                                                         parseEnum(CleanupItemState.class, state),
                                                                         parseEnum(CleanupAction.class, action),
                                                                         minSize,
                                                                         pageable),
                                        identityManager);
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

  @Secured("administrators")
  @GetMapping(path = "{id}/archive")
  @Operation(method = "GET", summary = "Download the CSV report of a cleanup campaign", description = "Download the CSV report of a campaign: generated live while item detail is retained, from the archive afterwards")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public ResponseEntity<byte[]> getCampaignArchive(
                                                   @Parameter(description = "Campaign identifier", required = true)
                                                   @PathVariable("id")
                                                   long id) {
    try {
      byte[] csv = campaignService.getArchiveCsv(id);
      return ResponseEntity.ok()
                           .header(HttpHeaders.CONTENT_DISPOSITION,
                                   "attachment; filename=\"cleanup-campaign-" + id + ".csv\"")
                           .contentType(MediaType.parseMediaType("text/csv"))
                           .body(csv);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @Secured("users")
  @GetMapping(path = "published/my-items", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "GET", summary = "Retrieve the current user's cleanup candidates", description = "Retrieve the currently relevant campaign's items owned by the user (own files and managed-space files), sorted by size descending")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public PagedResult<CampaignItemRestEntity> getMyItems(HttpServletRequest request,
                                                        @Parameter(description = "Page index")
                                                        @RequestParam(name = "page", required = false, defaultValue = "0")
                                                        int page,
                                                        @Parameter(description = "Page size")
                                                        @RequestParam(name = "size", required = false, defaultValue = "20")
                                                        int size) {
    try {
      return CleanupEntityBuilder.build(campaignService.getMyItems(request.getRemoteUser(), page, size), identityManager);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
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

  private Sort parseSort(String sort) {
    if (StringUtils.isBlank(sort)) {
      return Sort.by(Sort.Direction.DESC, "fileSize");
    }
    String[] parts = sort.split(",");
    String field = parts[0].trim();
    if (!SORTABLE_ITEM_FIELDS.contains(field)) {
      throw new IllegalArgumentException("cleanup.invalidSortField");
    }
    Sort.Direction direction = parts.length > 1 && StringUtils.equalsIgnoreCase(parts[1].trim(), "asc") ? Sort.Direction.ASC :
                                                                                                        Sort.Direction.DESC;
    return Sort.by(direction, field);
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
