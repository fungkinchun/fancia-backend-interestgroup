package com.fancia.backend.interestgroup.core.service

import com.fancia.backend.interestgroup.core.entity.InterestGroup
import com.fancia.backend.interestgroup.core.entity.InterestGroupMembership
import com.fancia.backend.interestgroup.core.entity.InterestGroupMembershipId
import com.fancia.backend.interestgroup.core.repository.InterestGroupMembershipRepository
import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import com.fancia.backend.interestgroup.external.CommonServiceClient
import com.fancia.backend.interestgroup.mapper.toDto
import com.fancia.backend.interestgroup.mapper.toEntity
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.core.exception.PremiumFeatureLimitException
import com.fancia.backend.shared.common.core.enums.ResourceVisibility
import com.fancia.backend.shared.common.core.utils.Slugify
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.common.tag.core.dto.CreateTagsRequest
import com.fancia.backend.shared.common.tag.core.dto.TagItemRequest
import com.fancia.backend.shared.interestgroup.core.dto.CreateInterestGroupRequest
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupResponse
import com.fancia.backend.shared.interestgroup.core.dto.UpdateInterestGroupRequest
import com.fancia.backend.shared.interestgroup.core.enums.InterestGroupRole
import com.fancia.backend.shared.interestgroup.core.enums.MembershipStatus
import com.fancia.backend.shared.interestgroup.core.exception.InterestGroupMembershipNotFoundException
import com.fancia.backend.shared.interestgroup.core.exception.InterestGroupNotFoundException
import com.fancia.backend.shared.user.core.support.PremiumLimits
import com.fancia.backend.shared.user.core.support.isPremiumClaim
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class InterestGroupService(
    private val interestGroupRepository: InterestGroupRepository,
    private val interestGroupMembershipRepository: InterestGroupMembershipRepository,
    private val commonServiceClient: CommonServiceClient,
    private val savedResourceService: SavedResourceService,
) {
    fun listSavedInterestGroups(jwt: Jwt, pageable: Pageable): Page<InterestGroupResponse> {
        val page = savedResourceService.listSavedPage(jwt, pageable)
        if (page.isEmpty) {
            return PageImpl(emptyList(), pageable, 0)
        }
        val ids = page.content.map { it.id.resourceId }
        val groupsById = interestGroupRepository.findAllById(ids).associateBy { it.id }
        val counts = acceptedMemberCounts(groupsById.keys)
        val responses = ids.mapNotNull { id ->
            val group = groupsById[id] ?: return@mapNotNull null
            group.toDto(counts[id] ?: 0L).also {
                it.savedByCurrentUser = true
            }
        }
        return PageImpl(responses, pageable, page.totalElements)
    }

    fun findById(id: UUID): InterestGroupResponse {
        return interestGroupRepository.findById(id)
            .map { it.toDto(acceptedMemberCount(it.id!!)) }
            .orElseThrow { InterestGroupNotFoundException(id) }
    }

    fun findByIdOrSlug(ref: String, jwt: Jwt? = null): InterestGroupResponse {
        val group = resolveByIdOrSlug(ref)
        val response = group.toDto(acceptedMemberCount(group.id!!))
        enrichSaved(response, jwt)
        return response
    }

    private fun enrichSaved(response: InterestGroupResponse, jwt: Jwt?) {
        val userId = jwt?.getClaimAsString("userId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val groupId = response.id
        if (userId == null || groupId == null) {
            response.savedByCurrentUser = null
            return
        }
        response.savedByCurrentUser = savedResourceService.isSaved(userId, groupId)
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
        val visible = groups.content.filter { it.visibility == ResourceVisibility.PUBLIC }
        val responses = visible.map { group ->
            group.toDto(counts[group.id] ?: 0L)
        }
        return PageImpl(responses, pageable, groups.totalElements)
    }

    fun findByIdAndCreatedBy(id: UUID, createdBy: UUID): InterestGroup? {
        return interestGroupRepository.findByIdAndCreatedBy(id, createdBy)
    }

    @Transactional
    fun create(request: @Valid CreateInterestGroupRequest, jwt: Jwt): InterestGroupResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        if (!PremiumLimits.allowsUnlimitedGroups(jwt.isPremiumClaim())) {
            val owned = interestGroupRepository.countByCreatedBy(currentUserId)
            if (owned >= PremiumLimits.GROUPS_FREE) {
                throw PremiumFeatureLimitException(
                    "Free plan allows up to ${PremiumLimits.GROUPS_FREE} groups. " +
                        "Upgrade to Fancia Premium for unlimited group creation.",
                )
            }
        }
        request.toEntity().let { it ->
            it.createdBy = currentUserId
            it.slug = allocateGroupSlug(request.name)
            applyTags(it.tags, request.tags)
            it.links.clear()
            it.links.addAll(request.links.map { link -> Link(type = link.type, url = link.url) })
            val interestGroup = interestGroupRepository.save(it)
            val ownerMembership = InterestGroupMembership().apply {
                this.interestGroup = interestGroup
                this.id = InterestGroupMembershipId(
                    interestGroupId = interestGroup.id!!,
                    userId = currentUserId,
                )
                this.role = InterestGroupRole.ADMIN
                this.status = MembershipStatus.ACCEPTED
                this.joinedAt = LocalDateTime.now()
            }
            interestGroup.memberships.add(ownerMembership)
            val saved = interestGroupRepository.save(interestGroup)
            return saved.toDto(1)
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
