package com.fancia.backend.interestgroup.core.service

import com.fancia.backend.interestgroup.core.entity.InterestGroupMembershipId
import com.fancia.backend.interestgroup.core.repository.InterestGroupMembershipRepository
import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import com.fancia.backend.interestgroup.mapper.InterestGroupMembershipMapper
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
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class InterestGroupMembershipService(
    private val interestGroupRepository: InterestGroupRepository,
    private val interestGroupMembershipRepository: InterestGroupMembershipRepository,
    private val interestGroupMembershipMapper: InterestGroupMembershipMapper,
) {
    @Transactional
    fun create(request: @Valid CreateInterestGroupMembershipRequest, jwt: Jwt): InterestGroupMembershipResponse {
        val userId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val interestGroup = interestGroupRepository.findByIdOrNull(request.interestGroupId)
            ?: throw InterestGroupNotFoundException(request.interestGroupId)
        if (interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserId(
                request.interestGroupId,
                request.userId
            )
        ) {
            throw InterestGroupMembershipAlreadyExistsException(request.interestGroupId, userId)
        }
        interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserIdAndRole(
            request.interestGroupId,
            userId,
            InterestGroupRole.ADMIN
        ) || userId == request.userId || throw InterestGroupMembershipAccessDeniedException(
            request.interestGroupId,
            userId
        )
        val membership = interestGroupMembershipMapper.toBean(request)
        membership.interestGroup = interestGroup
        membership.id = InterestGroupMembershipId(
            interestGroupId = request.interestGroupId,
            userId = request.userId
        )
        return interestGroupMembershipRepository.save(membership).let(interestGroupMembershipMapper::toDto)
    }

    @Transactional
    fun create(interestGroupId: UUID, jwt: Jwt): InterestGroupMembershipResponse {
        val userId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        return create(
            CreateInterestGroupMembershipRequest(
                interestGroupId = interestGroupId,
                userId = userId,
                payload = ""
            ),
            jwt
        )
    }

    @Transactional
    fun update(request: @Valid UpdateInterestGroupMembershipRequest, jwt: Jwt): InterestGroupMembershipResponse {
        val userId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserId(request.interestGroupId, request.userId)
                || throw InterestGroupMembershipNotFoundException(request.interestGroupId, request.userId)
        val isAdmin = interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserIdAndRole(
            request.interestGroupId,
            userId,
            InterestGroupRole.ADMIN
        )
        when {
            !isAdmin && userId != request.userId ->
                throw InterestGroupMembershipAccessDeniedException(request.interestGroupId, userId)

            !isAdmin && request.status != MembershipStatus.WITHDREW ->
                throw InterestGroupStatusChangeAccessDeniedException()
        }
        interestGroupMembershipMapper.toBean(request).let {
            return interestGroupMembershipMapper.toDto(interestGroupMembershipRepository.save(it))
        }
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
        pageable: Pageable
    ): Page<InterestGroupMembershipResponse> {
        val memberships = interestGroupMembershipRepository.findByIdUserIdAndRole(userId, role, pageable)
        return memberships.map(interestGroupMembershipMapper::toDto)
    }
}