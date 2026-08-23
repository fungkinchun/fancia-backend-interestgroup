package com.fancia.backend.interestgroup.core.service

import com.fancia.backend.interestgroup.core.entity.InterestGroup
import com.fancia.backend.interestgroup.core.repository.InterestGroupMembershipRepository
import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import com.fancia.backend.interestgroup.external.CommonServiceClient
import com.fancia.backend.interestgroup.mapper.toDto
import com.fancia.backend.interestgroup.mapper.toEntity
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.core.utils.Slugify
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.common.tag.core.dto.CreateTagsRequest
import com.fancia.backend.shared.common.tag.core.dto.TagItemRequest
import com.fancia.backend.shared.interestgroup.core.dto.CreateInterestGroupRequest
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupResponse
import com.fancia.backend.shared.interestgroup.core.dto.UpdateInterestGroupRequest
import com.fancia.backend.shared.interestgroup.core.enums.MembershipStatus
import com.fancia.backend.shared.interestgroup.core.exception.InterestGroupMembershipNotFoundException
import com.fancia.backend.shared.interestgroup.core.exception.InterestGroupNotFoundException
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
    private val interestGroupMembershipRepository: InterestGroupMembershipRepository,
    private val commonServiceClient: CommonServiceClient,
) {
    fun findById(id: UUID): InterestGroupResponse {
        return interestGroupRepository.findById(id)
            .map { it.toDto(acceptedMemberCount(it.id!!)) }
            .orElseThrow { InterestGroupNotFoundException(id) }
    }

    fun findByIdOrSlug(ref: String): InterestGroupResponse {
        val group = resolveByIdOrSlug(ref)
        return group.toDto(acceptedMemberCount(group.id!!))
    }

    fun resolveByIdOrSlug(ref: String): InterestGroup {
        val trimmed = ref.trim()
        if (trimmed.isEmpty()) throw InterestGroupNotFoundException(ref)
        val asUuid = runCatching { UUID.fromString(trimmed) }.getOrNull()
        if (asUuid != null) {
            return interestGroupRepository.findById(asUuid).orElseThrow { InterestGroupNotFoundException(asUuid) }
        }
        return interestGroupRepository.findBySlug(trimmed).orElseThrow { InterestGroupNotFoundException(trimmed) }
    }

    fun findAll(
        name: String?,
        description: String?,
        tagIds: List<UUID>?,
        pageable: Pageable
    ): Page<InterestGroupResponse> {
        val trimmedName = name?.trim().orEmpty()
        val trimmedDescription = description?.trim().orEmpty()
        val hasText = trimmedName.isNotEmpty() || trimmedDescription.isNotEmpty()
        val hasTagIds = !tagIds.isNullOrEmpty()

        val groups = when {
            !hasText && !hasTagIds -> interestGroupRepository.findAll(pageable)

            !hasText && hasTagIds ->
                interestGroupRepository.findByTagIdIn(tagIds!!, pageable)

            else ->
                interestGroupRepository.search(
                    trimmedName,
                    trimmedDescription,
                    hasTagIds,
                    tagIds.orEmpty(),
                    pageable,
                )
        }
        val counts = acceptedMemberCounts(groups.content.mapNotNull { it.id })
        return groups.map { group ->
            group.toDto(counts[group.id] ?: 0L)
        }
    }

    fun findByIdAndCreatedBy(id: UUID, createdBy: UUID): InterestGroup? {
        return interestGroupRepository.findByIdAndCreatedBy(id, createdBy)
    }

    @Transactional
    fun create(request: @Valid CreateInterestGroupRequest, jwt: Jwt): InterestGroupResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        request.toEntity().let { it ->
            it.createdBy = currentUserId
            it.slug = allocateGroupSlug(request.name)
            applyTags(it.tags, request.tags)
            it.links.clear()
            it.links.addAll(request.links.map { link -> Link(type = link.type, url = link.url) })
            val interestGroup = interestGroupRepository.save(it)
            // Creator membership is added by the controller after create; count starts at 0.
            return interestGroup.toDto(0)
        }
    }

    @Transactional
    fun update(id: UUID, request: @Valid UpdateInterestGroupRequest, jwt: Jwt): InterestGroupResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val interestGroup = interestGroupRepository.findByIdAndCreatedBy(id, currentUserId)
            ?: throw InterestGroupMembershipNotFoundException(id, currentUserId)
        request.toEntity(interestGroup).let {
            applyTags(it.tags, request.tags)
            it.links.clear()
            it.links.addAll(request.links.map { link -> Link(type = link.type, url = link.url) })
            val saved = interestGroupRepository.save(it)
            return saved.toDto(acceptedMemberCount(saved.id!!))
        }
    }

    @Transactional
    fun removeTagFromAllGroups(tagId: UUID) {
        val groupsWithTag = interestGroupRepository.findByTagId(tagId)
        for (group in groupsWithTag) {
            group.tags.remove(tagId)
        }
        if (groupsWithTag.isNotEmpty()) {
            interestGroupRepository.saveAll(groupsWithTag)
        }
    }

    private fun acceptedMemberCount(groupId: UUID): Long =
        interestGroupMembershipRepository.countByIdInterestGroupIdAndStatus(
            groupId,
            MembershipStatus.ACCEPTED,
        )

    private fun acceptedMemberCounts(groupIds: Collection<UUID>): Map<UUID, Long> {
        if (groupIds.isEmpty()) return emptyMap()
        return interestGroupMembershipRepository
            .countByInterestGroupIdInAndStatus(groupIds, MembershipStatus.ACCEPTED)
            .associate { row ->
                val id = row[0] as UUID
                val count = (row[1] as Number).toLong()
                id to count
            }
    }

    private fun applyTags(tags: MutableSet<UUID>, requestTags: Set<TagItemRequest>) {
        tags.clear()
        if (requestTags.isEmpty()) return
        val resolved = commonServiceClient.createTags(
            CreateTagsRequest(tags = requestTags.toList()),
            size = requestTags.size,
        ).content.mapNotNull { it.id }
        tags.addAll(resolved)
    }

    private fun allocateGroupSlug(name: String): String =
        Slugify.allocateUnique(name, fallback = "group") { interestGroupRepository.existsBySlug(it) }
}
