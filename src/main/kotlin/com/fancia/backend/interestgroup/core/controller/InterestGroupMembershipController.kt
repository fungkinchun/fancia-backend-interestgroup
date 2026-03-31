package com.fancia.backend.interestgroup.core.controller

import com.fancia.backend.interestgroup.core.service.InterestGroupMembershipService
import com.fancia.backend.shared.interestgroup.core.dto.CreateInterestGroupMembershipRequest
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupMembershipResponse
import com.fancia.backend.shared.interestgroup.core.dto.UpdateInterestGroupMembershipRequest
import com.fancia.backend.shared.interestgroup.core.enums.InterestGroupRole
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/interestGroupMemberships")
@Tag(name = "Interest Group Membership", description = "Interest Group Membership endpoints")
@SecurityRequirement(name = "bearerAuth")
class InterestGroupMembershipController(
    private val interestGroupMembershipService: InterestGroupMembershipService
) {
    @Operation(
        summary = "Create interest group member",
        description = "Returns the newly created interest group member"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Member created"),
        ]
    )
    @PostMapping
    fun createInterestGroupMembership(
        @RequestBody request: CreateInterestGroupMembershipRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<InterestGroupMembershipResponse> {
        val member = interestGroupMembershipService.create(request, jwt)
        return ResponseEntity.ok(member)
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_interest_group_membership.update')")
    fun updateInterestGroupMembership(
        @PathVariable id: UUID,
        @RequestBody request: UpdateInterestGroupMembershipRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        interestGroupMembershipService.update(request, jwt)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/user/{userId}")
    fun listInterestGroupMembershipsForUser(
        @PathVariable("userId") userId: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Interest group role to filter by")
        role: InterestGroupRole = InterestGroupRole.ADMIN,
        @PageableDefault(size = 20)
        pageable: Pageable
    ): ResponseEntity<Page<InterestGroupMembershipResponse>> {
        val memberships = interestGroupMembershipService.findAllForUser(userId, role, pageable)
        return ResponseEntity.ok(memberships)
    }
}