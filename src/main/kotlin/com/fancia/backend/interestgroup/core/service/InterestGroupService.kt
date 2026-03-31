package com.fancia.backend.interestgroup.core.service

import com.fancia.backend.interestgroup.core.entity.InterestGroup
import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import com.fancia.backend.interestgroup.external.CommonServiceClient
import com.fancia.backend.interestgroup.mapper.InterestGroupMapper
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.interestgroup.core.dto.CreateInterestGroupRequest
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupResponse
import com.fancia.backend.shared.interestgroup.core.dto.UpdateInterestGroupRequest
import com.fancia.backend.shared.interestgroup.core.exception.InterestGroupMembershipNotFoundException
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class InterestGroupService(
    private val interestGroupRepository: InterestGroupRepository,
    private val interestGroupMapper: InterestGroupMapper,
    private val interestGroupMembershipService: InterestGroupMembershipService,
    private val commonServiceClient: CommonServiceClient
) {
    fun findAll(
        name: String?,
        description: String?,
        tags: String?,
        pageable: Pageable
    ): Page<InterestGroupResponse> {
        val groups = when {
            name.isNullOrBlank() && description.isNullOrBlank() && tags.isNullOrBlank() ->
                interestGroupRepository.findAll(pageable)

            else -> {
                interestGroupRepository.findAll(
                    name?.trim() ?: "",
                    description?.trim() ?: "",
                    tags?.trim() ?: "",
                    pageable
                )
            }
        }
        return groups.map(interestGroupMapper::toDto)
    }

    fun findByIdAndCreatedBy(id: UUID, createdBy: UUID): InterestGroup? {
        return interestGroupRepository.findByIdAndCreatedBy(id, createdBy)
    }

    @Transactional
    fun create(request: @Valid CreateInterestGroupRequest, jwt: Jwt): InterestGroupResponse {
        val userId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        interestGroupMapper.toBean(request).let { it ->
            it.createdBy = userId
//            val response = commonServiceClient.getTags(request.tags)
//            it.tags.clear()
//            it.tags.addAll(response.tags.map { t -> t.name })
            val interestGroup = interestGroupRepository.save(it)

            return interestGroup.let(interestGroupMapper::toDto)
        }
    }

    @Transactional
    fun update(id: UUID, request: @Valid UpdateInterestGroupRequest, jwt: Jwt): InterestGroupResponse {
        val userId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val interestGroup = interestGroupRepository.findByIdAndCreatedBy(id, userId)
            ?: throw InterestGroupMembershipNotFoundException(id, userId)
        interestGroupMapper.toBean(request, interestGroup).let {
            return interestGroupRepository.save(it).let(interestGroupMapper::toDto)
        }
    }

    @Transactional
    fun removeTagFromAllGroups(tagName: String) {
        if (tagName.isBlank()) return
        val groupsWithTag = interestGroupRepository.findByTagsContaining(tagName)
        for (group in groupsWithTag) {
            group.tags.remove(tagName)
        }
        if (groupsWithTag.isNotEmpty()) {
            interestGroupRepository.saveAll(groupsWithTag)
        }
    }
}