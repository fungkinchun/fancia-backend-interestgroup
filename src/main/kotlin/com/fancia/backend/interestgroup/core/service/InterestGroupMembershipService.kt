package com.fancia.backend.interestgroup.core.service

import com.fancia.backend.interestgroup.core.entity.InterestGroupMembershipId
import com.fancia.backend.interestgroup.core.repository.InterestGroupMembershipRepository
import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import com.fancia.backend.interestgroup.mapper.toDto
import com.fancia.backend.interestgroup.mapper.toEntity
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
) {
    @Transactional
    fun create(
        interestGroupId: UUID,
        request: @Valid CreateInterestGroupMembershipRequest,
        jwt: Jwt
    ): InterestGroupMembershipResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val interestGroup = interestGroupRepository.findByIdOrNull(interestGroupId)
            ?: throw InterestGroupNotFoundException(interestGroupId)
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
        // Join requests start PENDING; joinedAt is set only when status becomes ACCEPTED.
        membership.status = MembershipStatus.PENDING
        membership.joinedAt = null
        return interestGroupMembershipRepository.save(membership).toDto()
    }

    @Transactional
    fun update(
        interestGroupId: UUID,
        userId: UUID,
        request: @Valid UpdateInterestGroupMembershipRequest, jwt: Jwt
    ): InterestGroupMembershipResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserId(interestGroupId, userId)
                || throw InterestGroupMembershipNotFoundException(interestGroupId, userId)
        val isAdmin = interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserIdAndRole(
            interestGroupId,
            currentUserId,
            InterestGroupRole.ADMIN
        )
        when {
            !isAdmin && currentUserId != userId ->
                throw InterestGroupMembershipAccessDeniedException(interestGroupId, currentUserId)

            !isAdmin && request.status != MembershipStatus.WITHDREW ->
                throw InterestGroupStatusChangeAccessDeniedException()
        }
        val membership = interestGroupMembershipRepository.findByIdInterestGroupIdAndIdUserId(
            interestGroupId,
            userId
        ) ?: throw InterestGroupMembershipNotFoundException(interestGroupId, userId)
        val previousStatus = membership.status
        request.toEntity(membership)
        if (membership.status == MembershipStatus.ACCEPTED && previousStatus != MembershipStatus.ACCEPTED) {
            membership.joinedAt = LocalDateTime.now()
        }
        return interestGroupMembershipRepository.save(membership).toDto()
    }

    @Transactional
    fun removeMemberFromAllGroups(userId: UUID) {
        val memberships = interestGroupMembershipRepository.findByIdUserId(userId)
        memberships.forEach {
            interestGroupMembershipRepository.delete(it)
        }
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
        // ProfileResponse applies privacy: null groupsCount means section hidden.
        return user.groupsCount != null
    }
}