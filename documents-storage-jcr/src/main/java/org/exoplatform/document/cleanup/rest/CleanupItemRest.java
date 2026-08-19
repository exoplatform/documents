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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.document.cleanup.rest.model.KeepItemsRestEntity;
import org.exoplatform.document.cleanup.rest.model.KeepItemsResultRestEntity;
import org.exoplatform.document.cleanup.rest.util.CleanupEntityBuilder;
import org.exoplatform.document.cleanup.service.CleanupCampaignService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/cleanup/items")
public class CleanupItemRest {

  @Autowired
  private CleanupCampaignService campaignService;

  @Secured("users")
  @PostMapping("{id}/keep")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(method = "POST", summary = "Keep a cleanup candidate file", description = "Exempt one of the user's candidate files from the published campaign (ownership checked in the Service layer)")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "204", description = "Request fulfilled"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
    @ApiResponse(responseCode = "403", description = "Forbidden"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public void keepItem(HttpServletRequest request,
                       @Parameter(description = "Campaign item identifier", required = true)
                       @PathVariable("id")
                       long id) {
    try {
      campaignService.keepItem(id, request.getRemoteUser());
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Answers 200 with the per-item outcomes, NEVER a blanket 204: a bulk keep
   * continues past individual failures, so the UI has to be able to warn instead
   * of reporting a success when nothing was actually kept.
   */
  @Secured("users")
  @PostMapping(path = "keep", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "POST", summary = "Keep several cleanup candidate files", description = "Exempt several of the user's candidate files from the published campaign (ownership checked in the Service layer), continuing past individual failures and reporting them")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled, with the per-item outcomes"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
  })
  public KeepItemsResultRestEntity keepItems(HttpServletRequest request,
                                            @io.swagger.v3.oas.annotations.parameters.RequestBody
                                            @RequestBody
                                            KeepItemsRestEntity keepItemsEntity) {
    try {
      return CleanupEntityBuilder.build(campaignService.keepItems(keepItemsEntity.getItemIds(), request.getRemoteUser()));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @Secured("users")
  @PostMapping("{id}/unkeep")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(method = "POST", summary = "Un-keep a previously kept cleanup candidate file", description = "Undo the exemption of one of the user's kept files while the campaign is published (ownership checked in the Service layer)")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "204", description = "Request fulfilled"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
    @ApiResponse(responseCode = "403", description = "Forbidden"),
    @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public void unkeepItem(HttpServletRequest request,
                         @Parameter(description = "Campaign item identifier", required = true)
                         @PathVariable("id")
                         long id) {
    try {
      campaignService.unkeepItem(id, request.getRemoteUser());
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Answers 200 with the per-item outcomes, mirroring
   * {@link #keepItems(HttpServletRequest, KeepItemsRestEntity)}.
   */
  @Secured("users")
  @PostMapping(path = "unkeep", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(method = "POST", summary = "Un-keep several previously kept cleanup candidate files", description = "Undo the exemption of several of the user's kept files while the campaign is published (ownership checked in the Service layer), continuing past individual failures and reporting them")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled, with the per-item outcomes"),
    @ApiResponse(responseCode = "400", description = "Bad Request"),
  })
  public KeepItemsResultRestEntity unkeepItems(HttpServletRequest request,
                                              @io.swagger.v3.oas.annotations.parameters.RequestBody
                                              @RequestBody
                                              KeepItemsRestEntity keepItemsEntity) {
    try {
      return CleanupEntityBuilder.build(campaignService.unkeepItems(keepItemsEntity.getItemIds(), request.getRemoteUser()));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

}
