package com.fancia.backend.interestgroup.core.message

import com.fancia.backend.interestgroup.core.service.InterestGroupMembershipService
import com.fancia.backend.shared.user.core.message.UserDeletedEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class UserConsumer(
    private val interestGroupMembershipService: InterestGroupMembershipService
) {
    // Group id must be unique per service: sharing one makes Kafka split the partitions between
    // them, so each deletion would reach only one of the two services.
    @KafkaListener(topics = ["users"], groupId = "interestgroup-user-deletion")
    fun onUserDeleted(event: UserDeletedEvent) {
        interestGroupMembershipService.removeMemberFromAllGroups(event.id)
    }
}