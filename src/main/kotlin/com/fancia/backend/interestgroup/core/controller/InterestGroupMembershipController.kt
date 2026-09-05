package com.fancia.backend.interestgroup.core.controller

import com.fancia.backend.interestgroup.core.service.InterestGroupMembershipService
import com.fancia.backend.shared.interestgroup.core.dto.CreateInterestGroupMembershipRequest
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupMembershipResponse
import com.fancia.backend.shared.interestgroup.core.dto.UpdateInterestGroupMembershipRequest
import com.fancia.backend.shared.interestgroup.core.enums.InterestGroupRole
import com.fancia.backend.shared.interestgroup.core.enums.MembershipStatus
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
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/interest-groups")
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
    @PostMapping("/{interestGroupId}/memberships")
    fun createInterestGroupMembership(
        @PathVariable interestGroupId: UUID,
        @RequestParam(required = false) invite: String?,
        @RequestBody request: CreateInterestGroupMembershipRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<InterestGroupMembershipResponse> {
        val member = interestGroupMembershipService.create(interestGroupId, request, jwt, invite)
        return ResponseEntity.ok(member)
    }

    @PatchMapping("/{interestGroupId}/memberships/{userId}")
    @Operation(
        summary = "Update interest group membership",
        description = "Admins can change another member's status (accept, deny, ban) and/or role " +
            "(promote to ADMIN / demote to MEMBER). Members may only set their own status to WITHDREW. " +
            "Requires a signed-in user.",
    )
    fun updateInterestGroupMembership(
        @PathVariable interestGroupId: UUID,
        @PathVariable userId: UUID,
        @RequestBody request: UpdateInterestGroupMembershipRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        interestGroupMembershipService.update(interestGroupId, userId, request, jwt)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/{interestGroupId}/memberships")
    @Operation(
        summary = "List memberships in an interest group",
        description = "Returns memberships for the group. Default status is ACCEPTED (public). " +
            "Non-ACCEPTED statuses (e.g. PENDING) require an ADMIN membership on the group.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "List of interest group memberships returned"),
        ],
    )
    fun listMembershipsInGroup(
        @PathVariable interestGroupId: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Optional role filter")
        role: InterestGroupRole? = null,
        @RequestParam(required = false)
        @Parameter(description = "Membership status filter; defaults to ACCEPTED")
        status: MembershipStatus? = null,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<List<InterestGroupMembershipResponse>> =
        ResponseEntity.ok(
            interestGroupMembershipService.listMembershipsInGroup(
                interestGroupId,
                role,
                status,
                jwt,
            ),
        )

    @GetMapping("/users/{userId}/memberships")
    @Operation(
        summary = "List interest group memberships for user",
        description = "Returns a paginated list of interest group memberships for the specified user, optionally filtered by role. Hidden unless privacy.showGroups is explicitly true, except when the viewer is that user."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "List of interest group memberships returned"),
        ]
    )
    fun listInterestGroupMembershipsForUser(
        @PathVariable userId: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Interest group role to filter by")
        role: InterestGroupRole = InterestGroupRole.ADMIN,
        @PageableDefault(size = 20)
        pageable: Pageable,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Page<InterestGroupMembershipResponse>> {
        val memberships = interestGroupMembershipService.findAllForUser(userId, role, pageable, jwt)
        return ResponseEntity.ok(memberships)
    }
}