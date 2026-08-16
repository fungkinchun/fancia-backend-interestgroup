package com.fancia.backend.interestgroup.mapper

import com.fancia.backend.interestgroup.core.entity.InterestGroup
import com.fancia.backend.interestgroup.core.entity.InterestGroupMembership
import com.fancia.backend.shared.common.social.core.dto.LinkResponse
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.interestgroup.core.dto.CreateInterestGroupMembershipRequest
import com.fancia.backend.shared.interestgroup.core.dto.CreateInterestGroupRequest
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupMembershipResponse
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupResponse
import com.fancia.backend.shared.interestgroup.core.dto.UpdateInterestGroupMembershipRequest
import com.fancia.backend.shared.interestgroup.core.dto.UpdateInterestGroupRequest

fun InterestGroup.toDto(): InterestGroupResponse =
    InterestGroupResponse(
        id = id,
        name = name,
        description = description,
        createdBy = createdBy,
        createdAt = createdAt,
        tags = tags,
        links = links.map { it.toDto() },
    )

fun CreateInterestGroupRequest.toEntity(): InterestGroup =
    InterestGroup().apply {
        name = this@toEntity.name
        description = this@toEntity.description
    }

fun UpdateInterestGroupRequest.toEntity(interestGroup: InterestGroup): InterestGroup {
    interestGroup.description = description
    return interestGroup
}

fun InterestGroupResponse.toEntity(): InterestGroup =
    InterestGroup().apply {
        id = this@toEntity.id
        name = this@toEntity.name
        description = this@toEntity.description
        createdBy = this@toEntity.createdBy
        createdAt = this@toEntity.createdAt
        tags = this@toEntity.tags.toMutableSet()
        links = this@toEntity.links.map { Link(type = it.type, url = it.url) }.toMutableSet()
    }

fun InterestGroupMembership.toDto(): InterestGroupMembershipResponse =
    InterestGroupMembershipResponse(
        interestGroupId = id?.interestGroupId,
        userId = id?.userId,
        status = status,
        role = role,
        joinedAt = joinedAt,
    )

fun CreateInterestGroupMembershipRequest.toEntity(): InterestGroupMembership =
    InterestGroupMembership()

fun UpdateInterestGroupMembershipRequest.toEntity(membership: InterestGroupMembership): InterestGroupMembership {
    membership.status = status
    return membership
}

private fun Link.toDto(): LinkResponse =
    LinkResponse(type = type, url = url)
