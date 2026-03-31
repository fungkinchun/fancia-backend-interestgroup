package com.fancia.backend.interestgroup.core.message

import com.fancia.backend.interestgroup.core.service.InterestGroupMembershipService
import com.fancia.backend.shared.user.core.message.UserDeletedEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class UserConsumer(
    private val interestGroupMembershipService: InterestGroupMembershipService
) {
    @KafkaListener(topics = ["users"], groupId = "deletion")
    fun onUserDeleted(event: UserDeletedEvent) {
        interestGroupMembershipService.removeMemberFromAllGroups(event.id)
    }
}