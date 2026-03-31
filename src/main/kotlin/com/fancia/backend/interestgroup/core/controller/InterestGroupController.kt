package com.fancia.backend.interestgroup.core.controller

import com.fancia.backend.interestgroup.core.service.InterestGroupMembershipService
import com.fancia.backend.interestgroup.core.service.InterestGroupService
import com.fancia.backend.shared.interestgroup.core.dto.CreateInterestGroupRequest
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupResponse
import com.fancia.backend.shared.interestgroup.core.dto.UpdateInterestGroupRequest
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
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/interestGroups")
@Tag(name = "Interest Groups", description = "Interest Group endpoints")
@SecurityRequirement(name = "bearerAuth")
class InterestGroupController(
    private val interestGroupService: InterestGroupService,
    private val interestGroupMembershipService: InterestGroupMembershipService
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
        @RequestBody request: CreateInterestGroupRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<InterestGroupResponse> {
        val interestGroup = interestGroupService.create(request, jwt)
        interestGroupMembershipService.create(
            interestGroupId = interestGroup.id!!,
            jwt
        )
        return ResponseEntity.ok(interestGroup)
    }

    @PutMapping("/{id}")
    fun updateInterestGroup(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UpdateInterestGroupRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<InterestGroupResponse> {
        return ResponseEntity.ok(interestGroupService.update(id, request, jwt))
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
        @Parameter(description = "Fuzzy search term for tags, use comma to separate multiple tags")
        tags: String? = null,
        @PageableDefault(size = 20)
        pageable: Pageable
    ): ResponseEntity<Page<InterestGroupResponse>> {
        val groups = interestGroupService.findAll(name, description, tags, pageable)
        return ResponseEntity.ok(groups)
    }
}