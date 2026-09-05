package com.fancia.backend.interestgroup.core.service

import com.fancia.backend.interestgroup.core.entity.InterestGroupMembershipId
import com.fancia.backend.interestgroup.core.repository.InterestGroupMembershipRepository
import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import com.fancia.backend.interestgroup.mapper.toDto
import com.fancia.backend.interestgroup.mapper.toEntity
import com.fancia.backend.shared.common.core.enums.ResourceVisibility
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.interestgroup.core.dto.CreateInterestGroupMembershipRequest
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupMembershipResponse
import com.fancia.backend.shared.interestgroup.core.dto.UpdateInterestGroupMembershipRequest
import com.fancia.backend.shared.interestgroup.core.enums.InterestGroupRole
import com.fancia.backend.shared.interestgroup.core.enums.MembershipStatus
import com.fancia.backend.shared.interestgroup.core.exception.*
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import com.fancia.backend.interestgroup.external.UserServiceClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.PageImpl
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class InterestGroupMembershipService(
    private val interestGroupRepository: InterestGroupRepository,
    private val interestGroupMembershipRepository: InterestGroupMembershipRepository,
    private val userServiceClient: UserServiceClient,
    private val interestGroupService: ObjectProvider<InterestGroupService>,
) {
    @Transactional
    fun create(
        interestGroupId: UUID,
        request: @Valid CreateInterestGroupMembershipRequest,
        jwt: Jwt,
        invite: String? = null,
    ): InterestGroupMembershipResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val interestGroup = interestGroupRepository.findByIdOrNull(interestGroupId)
            ?: throw InterestGroupNotFoundException(interestGroupId)
        interestGroupService.ifAvailable?.assertCanJoin(interestGroup, jwt, invite)
            ?: run {
                if (interestGroup.visibility == ResourceVisibility.PRIVATE) {
                    val token = interestGroup.inviteToken
                    val inviteOk = !token.isNullOrBlank() && !invite.isNullOrBlank() && token == invite
                    val member = interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserId(
                        interestGroupId,
                        currentUserId,
                    )
                    if (currentUserId != interestGroup.createdBy && !inviteOk && !member) {
                        throw InterestGroupNotFoundException(interestGroupId)
                    }
                }
            }
        if (interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserId(
                interestGroupId,
                currentUserId
            )
        ) {
            throw InterestGroupMembershipAlreadyExistsException(interestGroupId, currentUserId)
        }
        val membership = request.toEntity()
        membership.interestGroup = interestGroup
        membership.id = InterestGroupMembershipId(
            interestGroupId = interestGroupId,
            userId = currentUserId
        )
        membership.role = InterestGroupRole.MEMBER
        membership.status = MembershipStatus.PENDING
        membership.joinedAt = null
        val saved = interestGroupMembershipRepository.save(membership).toDto()
        interestGroupService.ifAvailable?.invalidateMembershipCaches(interestGroupId)
        return saved
    }

    @Transactional
    fun update(
        interestGroupId: UUID,
        userId: UUID,
        request: @Valid UpdateInterestGroupMembershipRequest, jwt: Jwt
    ): InterestGroupMembershipResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        if (request.status == null && request.role == null) {
            throw InterestGroupStatusChangeAccessDeniedException(
                message = "Provide a status and/or role to update",
            )
        }
        interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserId(interestGroupId, userId)
                || throw InterestGroupMembershipNotFoundException(interestGroupId, userId)
        val isAcceptedAdmin = interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserIdAndRoleAndStatus(
            interestGroupId,
            currentUserId,
            InterestGroupRole.ADMIN,
            MembershipStatus.ACCEPTED,
        )
        if (request.role != null && !isAcceptedAdmin) {
            throw InterestGroupRoleChangeAccessDeniedException(
                message = "Only accepted admins can promote or demote members",
            )
        }
        when {
            !isAcceptedAdmin && currentUserId != userId ->
                throw InterestGroupMembershipAccessDeniedException(interestGroupId, currentUserId)

            !isAcceptedAdmin && request.status != MembershipStatus.WITHDREW ->
                throw InterestGroupStatusChangeAccessDeniedException()
        }
        val membership = interestGroupMembershipRepository.findByIdInterestGroupIdAndIdUserId(
            interestGroupId,
            userId
        ) ?: throw InterestGroupMembershipNotFoundException(interestGroupId, userId)
        val previousStatus = membership.status
        request.role?.let { newRole ->
            applyRoleChange(
                interestGroupId = interestGroupId,
                membershipUserId = userId,
                membership = membership,
                newRole = newRole,
                requestedStatus = request.status,
            )
        }
        request.status?.let { membership.status = it }
        if (membership.status == MembershipStatus.ACCEPTED && previousStatus != MembershipStatus.ACCEPTED) {
            membership.joinedAt = LocalDateTime.now()
        }
        val saved = interestGroupMembershipRepository.save(membership).toDto()
        interestGroupService.ifAvailable?.invalidateMembershipCaches(interestGroupId)
        return saved
    }

    private fun applyRoleChange(
        interestGroupId: UUID,
        membershipUserId: UUID,
        membership: com.fancia.backend.interestgroup.core.entity.InterestGroupMembership,
        newRole: InterestGroupRole,
        requestedStatus: MembershipStatus?,
    ) {
        if (newRole == membership.role) return
        val group = interestGroupRepository.findByIdOrNull(interestGroupId)
            ?: throw InterestGroupNotFoundException(interestGroupId)
        if (group.createdBy == membershipUserId) {
            throw InterestGroupRoleChangeAccessDeniedException(
                message = "The group organiser cannot have their admin role changed",
            )
        }
        val effectiveStatus = requestedStatus ?: membership.status
        if (newRole == InterestGroupRole.ADMIN && effectiveStatus != MembershipStatus.ACCEPTED) {
            throw InterestGroupRoleChangeAccessDeniedException(
                message = "Only accepted members can be promoted to admin",
            )
        }
        if (newRole == InterestGroupRole.MEMBER && membership.role == InterestGroupRole.ADMIN) {
            val adminCount = interestGroupMembershipRepository.countByIdInterestGroupIdAndRoleAndStatus(
                interestGroupId,
                InterestGroupRole.ADMIN,
                MembershipStatus.ACCEPTED,
            )
            if (adminCount <= 1L) {
                throw InterestGroupRoleChangeAccessDeniedException(
                    message = "Cannot demote the last admin",
                )
            }
        }
        membership.role = newRole
    }

    @Transactional
    fun removeMemberFromAllGroups(userId: UUID) {
        val memberships = interestGroupMembershipRepository.findByIdUserId(userId)
        val groupIds = memberships.mapNotNull { it.id?.interestGroupId }.toSet()
        memberships.forEach {
            interestGroupMembershipRepository.delete(it)
        }
        groupIds.forEach { interestGroupService.ifAvailable?.invalidateMembershipCaches(it) }
    }

    fun findAllForUser(
        userId: UUID,
        role: InterestGroupRole = InterestGroupRole.ADMIN,
        pageable: Pageable,
        jwt: Jwt?,
    ): Page<InterestGroupMembershipResponse> {
        if (!canViewUserGroups(userId, jwt)) {
            return PageImpl(emptyList(), pageable, 0)
        }
        val memberships = interestGroupMembershipRepository.findByIdUserIdAndRole(userId, role, pageable)
        return memberships.map { it.toDto() }
    }

    fun listMembershipsInGroup(
        interestGroupId: UUID,
        role: InterestGroupRole? = null,
        status: MembershipStatus? = null,
        jwt: Jwt? = null,
    ): List<InterestGroupMembershipResponse> {
        interestGroupRepository.findByIdOrNull(interestGroupId)
            ?: throw InterestGroupNotFoundException(interestGroupId)
        val effectiveStatus = status ?: MembershipStatus.ACCEPTED
        if (effectiveStatus != MembershipStatus.ACCEPTED) {
            val currentUserId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) }
                ?: throw InvalidAuthenticationException()
            val isAdmin = interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserIdAndRole(
                interestGroupId,
                currentUserId,
                InterestGroupRole.ADMIN,
            )
            if (!isAdmin) {
                throw InterestGroupMembershipAccessDeniedException(interestGroupId, currentUserId)
            }
        }
        val memberships = if (role != null) {
            interestGroupMembershipRepository.findByIdInterestGroupIdAndRoleAndStatus(
                interestGroupId,
                role,
                effectiveStatus,
            )
        } else {
            interestGroupMembershipRepository.findByIdInterestGroupIdAndStatus(
                interestGroupId,
                effectiveStatus,
            )
        }
        return memberships.map { it.toDto() }
    }

    private fun canViewUserGroups(targetUserId: UUID, jwt: Jwt?): Boolean {
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) }
        if (viewerId == targetUserId) return true
        val user = runCatching { userServiceClient.getUser(targetUserId) }.getOrNull() ?: return false
        return user.groupsCount != null
    }
}
