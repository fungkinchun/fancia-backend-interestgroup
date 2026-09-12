package com.fancia.backend.interestgroup.core.service

import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserResourceCleanupService(
    private val interestGroupRepository: InterestGroupRepository,
    private val interestGroupMembershipService: InterestGroupMembershipService,
) {
    @Transactional
    fun deleteResourcesForUser(userId: UUID) {
        interestGroupMembershipService.removeMemberFromAllGroups(userId)
        interestGroupRepository.findByCreatedBy(userId).forEach { group ->
            interestGroupRepository.delete(group)
        }
    }
}
