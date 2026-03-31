package com.fancia.backend.interestgroup.mapper

import com.fancia.backend.interestgroup.core.entity.InterestGroup
import com.fancia.backend.shared.interestgroup.core.dto.CreateInterestGroupRequest
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupResponse
import com.fancia.backend.shared.interestgroup.core.dto.UpdateInterestGroupRequest
import org.mapstruct.Mapper
import org.mapstruct.MappingTarget
import org.mapstruct.NullValueMappingStrategy
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = "spring",
    nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    nullValueMapMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
interface InterestGroupMapper {
    fun toDto(interestGroup: InterestGroup): InterestGroupResponse
    fun toBean(request: CreateInterestGroupRequest): InterestGroup
    fun toBean(request: UpdateInterestGroupRequest, @MappingTarget target: InterestGroup): InterestGroup
}