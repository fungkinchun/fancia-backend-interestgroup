package com.fancia.backend.interestgroup.core.message

import com.fancia.backend.interestgroup.core.service.InterestGroupService
import com.fancia.backend.shared.common.tag.core.message.TagDeletedEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class TagConsumer(
    private val interestGroupService: InterestGroupService
) {
    @KafkaListener(topics = ["tags"], groupId = "deletion")
    fun onTagDeleted(event: TagDeletedEvent) {
        interestGroupService.removeTagFromAllGroups(event.id)
    }
}