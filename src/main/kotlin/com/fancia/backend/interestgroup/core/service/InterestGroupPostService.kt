package com.fancia.backend.interestgroup.core.service

import com.fancia.backend.interestgroup.core.repository.InterestGroupMembershipRepository
import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import com.fancia.backend.interestgroup.external.CommonInternalClient
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.post.core.dto.CastPollVoteRequest
import com.fancia.backend.shared.common.post.core.enums.PostKind
import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.CreatePostRequest
import com.fancia.backend.shared.common.post.core.dto.PostMediaItem
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
import com.fancia.backend.shared.common.post.core.exception.PostAccessDeniedException
import com.fancia.backend.shared.interestgroup.core.enums.MembershipStatus
import com.fancia.backend.shared.interestgroup.core.exception.InterestGroupNotFoundException
import com.fancia.backend.shared.upload.storage.core.enums.UploadScope
import com.fancia.backend.shared.upload.storage.core.service.FileStorageService
import com.fancia.backend.shared.upload.storage.core.service.moveTmpToDedicatedPath
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.util.*

@Service
class InterestGroupPostService(
    private val interestGroupRepository: InterestGroupRepository,
    private val interestGroupMembershipRepository: InterestGroupMembershipRepository,
    private val commonInternalClient: CommonInternalClient,
    private val jsonMapper: JsonMapper,
    private val fileUploadService: FileStorageService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun create(groupId: UUID, request: CreatePostBody, jwt: Jwt): PostResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        if (!interestGroupRepository.existsById(groupId)) {
            throw InterestGroupNotFoundException(groupId)
        }
        if (!interestGroupMembershipRepository.existsByIdInterestGroupIdAndIdUserIdAndStatus(
                groupId,
                currentUserId,
                MembershipStatus.ACCEPTED,
            )
        ) {
            throw PostAccessDeniedException(groupId)
        }
        val internalRequest = CreatePostRequest(
            targetId = groupId,
            authorUserId = currentUserId,
            body = request.body,
            media = dedicateMedia(request.media, groupId),
            status = request.status,
            expiredAt = request.expiredAt,
            kind = request.kind,
            poll = request.poll,
        )
        log.debug("common-api createPost payload: {}", jsonMapper.writeValueAsString(internalRequest))
        return commonInternalClient.createPost(internalRequest)
    }

    fun update(
        groupId: UUID,
        postId: UUID,
        request: UpdatePostRequest,
        jwt: Jwt,
    ): PostResponse {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        if (!interestGroupRepository.existsById(groupId)) {
            throw InterestGroupNotFoundException(groupId)
        }
        val scopedRequest = request.copy(media = dedicateMedia(request.media, groupId))
        log.debug("common-api updatePost payload: {}", jsonMapper.writeValueAsString(scopedRequest))
        val post = commonInternalClient.updatePost(postId, scopedRequest)
        if (post.targetId != groupId) {
            throw InterestGroupNotFoundException(groupId)
        }
        return post
    }

    fun like(groupId: UUID, postId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(groupId, postId)
        commonInternalClient.likePost(postId)
    }

    fun unlike(groupId: UUID, postId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(groupId, postId)
        commonInternalClient.unlikePost(postId)
    }

    fun vote(groupId: UUID, postId: UUID, request: CastPollVoteRequest, jwt: Jwt): PostResponse {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(groupId, postId)
        val post = commonInternalClient.voteOnPost(postId, request)
        if (post.targetId != groupId) {
            throw InterestGroupNotFoundException(groupId)
        }
        return post
    }

    fun list(
        groupId: UUID,
        kind: PostKind? = null,
        openOnly: Boolean = false,
        pageable: Pageable,
    ): Page<PostResponse> {
        if (!interestGroupRepository.existsById(groupId)) {
            throw InterestGroupNotFoundException(groupId)
        }
        return commonInternalClient.listPosts(groupId, kind, openOnly, pageable)
    }

    fun get(groupId: UUID, postId: UUID): PostResponse {
        if (!interestGroupRepository.existsById(groupId)) {
            throw InterestGroupNotFoundException(groupId)
        }
        val post = commonInternalClient.getPost(postId)
        if (post.targetId != groupId) {
            throw InterestGroupNotFoundException(groupId)
        }
        return post
    }

    private fun dedicateMedia(media: List<PostMediaItem>, groupId: UUID): List<PostMediaItem> =
        media.map { item ->
            item.copy(
                objectKey = fileUploadService.moveTmpToDedicatedPath(
                    item.objectKey,
                    UploadScope.INTEREST_GROUP,
                    groupId,
                ),
            )
        }
}
