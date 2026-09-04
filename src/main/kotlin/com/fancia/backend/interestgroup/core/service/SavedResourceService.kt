package com.fancia.backend.interestgroup.core.service

import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import com.fancia.backend.interestgroup.core.repository.SavedResourceRepository
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.saved.core.dto.SavedResourceResponse
import com.fancia.backend.shared.common.saved.core.entity.SavedResource
import com.fancia.backend.shared.common.saved.core.entity.SavedResourceId
import com.fancia.backend.shared.interestgroup.core.exception.InterestGroupNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SavedResourceService(
    private val savedResourceRepository: SavedResourceRepository,
    private val interestGroupRepository: InterestGroupRepository,
) {
    @Transactional
    fun save(interestGroupId: UUID, jwt: Jwt): SavedResourceResponse {
        val userId = currentUserId(jwt)
        if (!interestGroupRepository.existsById(interestGroupId)) {
            throw InterestGroupNotFoundException(interestGroupId)
        }
        val id = SavedResourceId(userId = userId, resourceId = interestGroupId)
        val saved = savedResourceRepository.findById(id).orElse(null)
            ?: savedResourceRepository.save(SavedResource(id))
        return SavedResourceResponse(resourceId = saved.id.resourceId, createdAt = saved.createdAt)
    }

    @Transactional
    fun unsave(interestGroupId: UUID, jwt: Jwt) {
        val userId = currentUserId(jwt)
        savedResourceRepository.deleteByIdUserIdAndIdResourceId(userId, interestGroupId)
    }

    @Transactional(readOnly = true)
    fun listSavedPage(jwt: Jwt, pageable: Pageable): Page<SavedResource> {
        val userId = currentUserId(jwt)
        return savedResourceRepository.findByIdUserIdOrderByCreatedAtDesc(userId, pageable)
    }

    @Transactional(readOnly = true)
    fun isSaved(userId: UUID, interestGroupId: UUID): Boolean =
        savedResourceRepository.existsByIdUserIdAndIdResourceId(userId, interestGroupId)

    private fun currentUserId(jwt: Jwt): UUID =
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
}
