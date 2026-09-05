package com.fancia.backend.interestgroup.core.service

import tools.jackson.core.type.TypeReference
import com.fancia.backend.shared.common.redis.CacheKeys
import com.fancia.backend.shared.common.redis.CachedPage
import com.fancia.backend.shared.common.redis.RedisQueryCache
import com.fancia.backend.interestgroup.core.entity.InterestGroup
import com.fancia.backend.interestgroup.core.entity.InterestGroupMembership
import com.fancia.backend.interestgroup.core.entity.InterestGroupMembershipId
import com.fancia.backend.interestgroup.core.repository.EventOccurrenceRepository
import com.fancia.backend.interestgroup.core.repository.InterestGroupMembershipRepository
import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import com.fancia.backend.interestgroup.external.CommonServiceClient
import com.fancia.backend.interestgroup.mapper.toDto
import com.fancia.backend.interestgroup.mapper.toEntity
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.core.exception.PremiumFeatureLimitException
import com.fancia.backend.shared.common.core.enums.ResourceVisibility
import com.fancia.backend.shared.common.core.utils.InviteTokens
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
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@Service
class InterestGroupService(
    private val interestGroupRepository: InterestGroupRepository,
    private val interestGroupMembershipRepository: InterestGroupMembershipRepository,
    private val eventOccurrenceRepository: EventOccurrenceRepository,
    private val commonServiceClient: CommonServiceClient,
    private val savedResourceService: SavedResourceService,
    private val redisQueryCache: ObjectProvider<RedisQueryCache>,
) {
    fun listSavedInterestGroups(jwt: Jwt, pageable: Pageable): Page<InterestGroupResponse> {
        val page = savedResourceService.listSavedPage(jwt, pageable)
        if (page.isEmpty) {
            return PageImpl(emptyList(), pageable, 0)
        }
        val ids = page.content.map { it.id.resourceId }
        val groupsById = interestGroupRepository.findAllById(ids).associateBy { it.id }
        val counts = acceptedMemberCounts(groupsById.keys.filterNotNull())
        val responses = ids.mapNotNull { id ->
            val group = groupsById[id] ?: return@mapNotNull null
            group.toDto(counts[id] ?: 0L).also {
                it.savedByCurrentUser = true
            }
        }
        return PageImpl(responses, pageable, page.totalElements)
    }

    fun findById(id: UUID): InterestGroupResponse {
        val cache = redisQueryCache.ifAvailable
        if (cache != null) {
            return cache.getOrLoad(
                detailKey(id),
                LIST_TTL,
                object : TypeReference<InterestGroupResponse>() {},
            ) {
                interestGroupRepository.findById(id)
                    .map { it.toDto(acceptedMemberCount(it.id!!)) }
                    .orElseThrow { InterestGroupNotFoundException(id) }
            }
        }
        return interestGroupRepository.findById(id)
            .map { it.toDto(acceptedMemberCount(it.id!!)) }
            .orElseThrow { InterestGroupNotFoundException(id) }
    }

    fun findByIdOrSlug(ref: String, jwt: Jwt? = null, invite: String? = null): InterestGroupResponse {
        val group = syncInviteToken(resolveByIdOrSlug(ref))
        assertCanAccess(group, jwt, invite)
        val response = group.toDto(acceptedMemberCount(group.id!!))
        enrichSaved(response, jwt)
        exposeInviteTokenIfCreator(response, group, jwt)
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
        hasUpcomingEvents: Boolean,
        availableFrom: LocalDateTime?,
        availableTo: LocalDateTime?,
        pageable: Pageable
    ): Page<InterestGroupResponse> {
        val cache = redisQueryCache.ifAvailable
        if (cache != null) {
            val key = LIST_PREFIX + CacheKeys.hash(
                name?.trim(), description?.trim(), tagIds, hasUpcomingEvents,
                availableFrom, availableTo, pageable.pageNumber, pageable.pageSize,
            )
            val cached = cache.getOrLoad(
                key,
                LIST_TTL,
                object : TypeReference<CachedPage<InterestGroupResponse>>() {},
            ) {
                CachedPage.from(
                    loadList(name, description, tagIds, hasUpcomingEvents, availableFrom, availableTo, pageable),
                )
            }
            return cached.toPage(pageable)
        }
        return loadList(name, description, tagIds, hasUpcomingEvents, availableFrom, availableTo, pageable)
    }

    private fun loadList(
        name: String?,
        description: String?,
        tagIds: List<UUID>?,
        hasUpcomingEvents: Boolean,
        availableFrom: LocalDateTime?,
        availableTo: LocalDateTime?,
        pageable: Pageable,
    ): Page<InterestGroupResponse> {
        val groupIdFilter = resolveGroupIdFilter(hasUpcomingEvents, availableFrom, availableTo)
        if (groupIdFilter.active && groupIdFilter.ids.isEmpty()) {
            return PageImpl(emptyList(), pageable, 0)
        }

        val trimmedName = name?.trim().orEmpty()
        val trimmedDescription = description?.trim().orEmpty()
        val hasText = trimmedName.isNotEmpty() || trimmedDescription.isNotEmpty()
        val hasTagIds = !tagIds.isNullOrEmpty()

        val groups = when {
            !hasText && !hasTagIds ->
                interestGroupRepository.findAllFiltered(
                    groupIdFilter.active,
                    groupIdFilter.ids,
                    pageable,
                )

            !hasText && hasTagIds ->
                interestGroupRepository.findByTagIdIn(
                    tagIds!!,
                    groupIdFilter.active,
                    groupIdFilter.ids,
                    pageable,
                )

            else ->
                interestGroupRepository.search(
                    trimmedName,
                    trimmedDescription,
                    hasTagIds,
                    tagIds.orEmpty(),
                    groupIdFilter.active,
                    groupIdFilter.ids,
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

    private fun resolveGroupIdFilter(
        hasUpcomingEvents: Boolean,
        availableFrom: LocalDateTime?,
        availableTo: LocalDateTime?,
    ): GroupIdFilter {
        if (!hasUpcomingEvents) {
            return GroupIdFilter.inactive()
        }
        val windowFrom = availableFrom ?: LocalDateTime.now()
        val ids = eventOccurrenceRepository
            .findInterestGroupIdsWithUpcomingPublicEvents(windowFrom, availableTo)
            .toList()
        return GroupIdFilter(active = true, ids = ids)
    }

    private data class GroupIdFilter(
        val active: Boolean,
        val ids: List<UUID>,
    ) {
        companion object {
            private val PLACEHOLDER = UUID(0L, 0L)

            fun inactive() = GroupIdFilter(active = false, ids = listOf(PLACEHOLDER))
        }
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
            syncInviteTokenInPlace(it)
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
            invalidateGroupCaches(saved.id!!)
            return saved.toDto(1).also { exposeInviteTokenIfCreator(it, saved, jwt) }
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
            syncInviteTokenInPlace(it)
            val saved = interestGroupRepository.save(it)
            invalidateGroupCaches(saved.id!!)
            return saved.toDto(acceptedMemberCount(saved.id!!)).also {
                exposeInviteTokenIfCreator(it, saved, jwt)
            }
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
            redisQueryCache.ifAvailable?.evictByPrefix(LIST_PREFIX)
            groupsWithTag.mapNotNull { it.id }.forEach { invalidateDetail(it) }
        }
    }

    fun invalidateMembershipCaches(groupId: UUID) {
        invalidateGroupCaches(groupId)
    }

    private fun acceptedMemberCount(groupId: UUID): Long {
        val cache = redisQueryCache.ifAvailable
            ?: return interestGroupMembershipRepository.countByIdInterestGroupIdAndStatus(
                groupId,
                MembershipStatus.ACCEPTED,
            )
        return cache.getOrLoad(
            countKey(groupId),
            COUNT_TTL,
            object : TypeReference<Long>() {},
        ) {
            interestGroupMembershipRepository.countByIdInterestGroupIdAndStatus(
                groupId,
                MembershipStatus.ACCEPTED,
            )
        }
    }

    private fun acceptedMemberCounts(groupIds: Collection<UUID>): Map<UUID, Long> {
        if (groupIds.isEmpty()) return emptyMap()
        val cache = redisQueryCache.ifAvailable
        if (cache == null) {
            return loadAcceptedMemberCounts(groupIds)
        }
        val key = COUNTS_PREFIX + CacheKeys.hash(groupIds)
        return cache.getOrLoad(
            key,
            COUNT_TTL,
            object : TypeReference<Map<UUID, Long>>() {},
        ) {
            loadAcceptedMemberCounts(groupIds)
        }
    }

    private fun loadAcceptedMemberCounts(groupIds: Collection<UUID>): Map<UUID, Long> =
        interestGroupMembershipRepository
            .countByInterestGroupIdInAndStatus(groupIds, MembershipStatus.ACCEPTED)
            .associate { row ->
                val id = row[0] as UUID
                val count = (row[1] as Number).toLong()
                id to count
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

    private fun detailKey(id: UUID) = "$DETAIL_PREFIX$id"
    private fun countKey(id: UUID) = "$COUNT_PREFIX$id"

    private fun invalidateDetail(groupId: UUID) {
        val cache = redisQueryCache.ifAvailable ?: return
        cache.evict(detailKey(groupId))
        cache.evict(countKey(groupId))
    }

    private fun invalidateGroupCaches(groupId: UUID) {
        val cache = redisQueryCache.ifAvailable ?: return
        invalidateDetail(groupId)
        cache.evictByPrefix(LIST_PREFIX)
        cache.evictByPrefix(COUNTS_PREFIX)
    }

    private fun assertCanAccess(group: InterestGroup, jwt: Jwt?, invite: String?) {
        if (group.visibility != ResourceVisibility.PRIVATE) return
        val userId = jwtUserId(jwt)
        if (userId != null && userId == group.createdBy) return
        val token = group.inviteToken
        if (!token.isNullOrBlank() && !invite.isNullOrBlank() && token == invite) return
        val groupId = group.id
        if (userId != null && groupId != null &&
            interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserId(groupId, userId)
        ) {
            return
        }
        throw InterestGroupNotFoundException(group.id ?: group.slug)
    }

    fun assertCanJoin(group: InterestGroup, jwt: Jwt, invite: String?) {
        assertCanAccess(group, jwt, invite)
    }

    private fun syncInviteToken(group: InterestGroup): InterestGroup {
        if (!syncInviteTokenInPlace(group)) return group
        return interestGroupRepository.save(group)
    }

    private fun syncInviteTokenInPlace(group: InterestGroup): Boolean {
        if (group.visibility == ResourceVisibility.PRIVATE) {
            if (group.inviteToken.isNullOrBlank()) {
                group.inviteToken = InviteTokens.generate()
                return true
            }
            return false
        }
        if (group.inviteToken != null) {
            group.inviteToken = null
            return true
        }
        return false
    }

    private fun exposeInviteTokenIfCreator(
        response: InterestGroupResponse,
        group: InterestGroup,
        jwt: Jwt?,
    ) {
        val userId = jwtUserId(jwt)
        if (userId != null && userId == group.createdBy && group.visibility == ResourceVisibility.PRIVATE) {
            response.inviteToken = group.inviteToken
        } else {
            response.inviteToken = null
        }
    }

    private fun jwtUserId(jwt: Jwt?): UUID? =
        jwt?.getClaimAsString("userId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    companion object {
        private const val LIST_PREFIX = "ig:list:"
        private const val DETAIL_PREFIX = "ig:detail:"
        private const val COUNT_PREFIX = "ig:count:"
        private const val COUNTS_PREFIX = "ig:counts:"
        private val LIST_TTL = Duration.ofMinutes(3)
        private val COUNT_TTL = Duration.ofSeconds(60)
    }
}
