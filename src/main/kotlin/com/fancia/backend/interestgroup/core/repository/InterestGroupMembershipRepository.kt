package com.fancia.backend.interestgroup.core.repository

import com.fancia.backend.interestgroup.core.entity.InterestGroupMembership
import com.fancia.backend.shared.interestgroup.core.enums.InterestGroupRole
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface InterestGroupMembershipRepository : JpaRepository<InterestGroupMembership, Long> {
    fun findByIdInterestGroupIdAndIdUserId(
        interestGroupId: UUID,
        userId: UUID
    ): InterestGroupMembership?

    fun findByIdUserId(userId: UUID): List<InterestGroupMembership>
    fun findByIdUserIdAndRole(
        userId: UUID,
        role: InterestGroupRole = InterestGroupRole.ADMIN,
        pageable: Pageable
    ): Page<InterestGroupMembership>

    fun existsByIdInterestGroupIdAndIdUserId(
        interestGroupId: UUID,
        userId: UUID
    ): Boolean

    fun existsByIdInterestGroupIdAndIdUserIdAndRole(
        interestGroupId: UUID,
        userId: UUID,
        role: InterestGroupRole = InterestGroupRole.ADMIN
    ): Boolean
}