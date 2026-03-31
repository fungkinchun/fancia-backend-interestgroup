package com.fancia.backend.interestgroup.mapper

import com.fancia.backend.interestgroup.core.entity.InterestGroupMembership
import com.fancia.backend.shared.interestgroup.core.dto.CreateInterestGroupMembershipRequest
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupMembershipResponse
import com.fancia.backend.shared.interestgroup.core.dto.UpdateInterestGroupMembershipRequest
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.NullValueMappingStrategy
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = "spring",
    nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    nullValueMapMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
interface InterestGroupMembershipMapper {
    @Mapping(target = "interestGroupId", source = "id.interestGroupId")
    @Mapping(target = "userId", source = "id.userId")
    fun toDto(membership: InterestGroupMembership): InterestGroupMembershipResponse

    @Mapping(target = "interestGroup", ignore = true)
    fun toBean(membership: CreateInterestGroupMembershipRequest): InterestGroupMembership
    fun toBean(membership: UpdateInterestGroupMembershipRequest): InterestGroupMembership
}