package com.fancia.backend.interestgroup.core.controller

import com.fancia.backend.interestgroup.core.service.InterestGroupMembershipService
import com.fancia.backend.interestgroup.core.service.InterestGroupService
import com.fancia.backend.interestgroup.core.service.SavedResourceService
import com.fancia.backend.shared.common.saved.core.dto.SavedResourceResponse
import com.fancia.backend.shared.interestgroup.core.dto.*
import com.fancia.backend.shared.interestgroup.core.enums.MembershipStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/interest-groups")
@Tag(name = "Interest Groups", description = "Interest Group endpoints")
@SecurityRequirement(name = "bearerAuth")
class InterestGroupController(
    private val interestGroupService: InterestGroupService,
    private val interestGroupMembershipService: InterestGroupMembershipService,
    private val savedResourceService: SavedResourceService,
) {
    @Operation(
        summary = "Create interest group",
        description = "Returns the newly created interest group"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Interest group created"),
        ]
    )
    @PostMapping
    fun createInterestGroup(
        @RequestBody @Valid request: CreateInterestGroupRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<InterestGroupResponse> {
        val interestGroup = interestGroupService.create(request, jwt)
        val membership = interestGroupMembershipService.create(
            interestGroupId = interestGroup.id!!,
            CreateInterestGroupMembershipRequest(payload = ""),
            jwt
        )
        interestGroupMembershipService.update(
            interestGroupId = membership.interestGroupId!!,
            userId = membership.userId!!,
            UpdateInterestGroupMembershipRequest(
                status = MembershipStatus.ACCEPTED
            ),
            jwt
        )
        // Re-load so memberCount includes the creator's ACCEPTED membership.
        return ResponseEntity.ok(interestGroupService.findById(interestGroup.id!!))
    }

    @PutMapping("/{id}")
    fun updateInterestGroup(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UpdateInterestGroupRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<InterestGroupResponse> {
        return ResponseEntity.ok(interestGroupService.update(id, request, jwt))
    }

    @GetMapping("/me/saved")
    @Operation(summary = "List interest groups saved by the current user")
    fun listSaved(
        @AuthenticationPrincipal jwt: Jwt,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<InterestGroupResponse>> =
        ResponseEntity.ok(interestGroupService.listSavedInterestGroups(jwt, pageable))

    @PostMapping("/{id}/saved")
    @Operation(summary = "Save an interest group")
    fun saveInterestGroup(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<SavedResourceResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(savedResourceService.save(id, jwt))

    @DeleteMapping("/{id}/saved")
    @Operation(summary = "Unsave an interest group")
    fun unsaveInterestGroup(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        savedResourceService.unsave(id, jwt)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{ref}")
    @Operation(summary = "Get interest group by id or slug")
    fun getInterestGroup(
        @PathVariable ref: String,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<InterestGroupResponse> {
        return ResponseEntity.ok(interestGroupService.findByIdOrSlug(ref, jwt))
    }

    @GetMapping
    @Operation(
        summary = "List interest groups",
        description = "Returns a paginated list of interest groups. Supports fuzzy search by name and tag."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "List of interest groups returned"),
        ]
    )
    fun listInterestGroups(
        @RequestParam(required = false)
        @Parameter(description = "Fuzzy search term for group name")
        name: String?,
        @Parameter(description = "Fuzzy search term for group description")
        description: String?,
        @RequestParam(required = false)
        @Parameter(description = "Filter by tag ids (entities matching any of the ids)")
        tagIds: List<UUID> = emptyList(),
        @RequestParam(required = false, defaultValue = "false")
        @Parameter(description = "When true, only groups hosting upcoming public events in the availability window")
        hasUpcomingEvents: Boolean = false,
        @RequestParam(required = false)
        @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
        @Parameter(description = "Availability window start (defaults to now)")
        availableFrom: java.time.LocalDateTime? = null,
        @RequestParam(required = false)
        @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
        @Parameter(description = "Availability window end (exclusive)")
        availableTo: java.time.LocalDateTime? = null,
        @PageableDefault(size = 20)
        pageable: Pageable
    ): ResponseEntity<Page<InterestGroupResponse>> {
        val groups = interestGroupService.findAll(
            name,
            description,
            tagIds,
            hasUpcomingEvents,
            availableFrom,
            availableTo,
            pageable,
        )
        return ResponseEntity.ok(groups)
    }
}