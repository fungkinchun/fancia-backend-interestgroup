package com.fancia.backend.interestgroup.core.repository

import com.fancia.backend.interestgroup.core.entity.InterestGroupMembership
import com.fancia.backend.shared.interestgroup.core.enums.InterestGroupRole
import com.fancia.backend.shared.interestgroup.core.enums.MembershipStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    fun existsByIdInterestGroupIdAndIdUserIdAndRoleAndStatus(
        interestGroupId: UUID,
        userId: UUID,
        role: InterestGroupRole,
        status: MembershipStatus,
    ): Boolean

    fun existsByIdInterestGroupIdAndIdUserIdAndStatus(
        interestGroupId: UUID,
        userId: UUID,
        status: MembershipStatus,
    ): Boolean

    fun findByIdInterestGroupIdAndRoleAndStatus(
        interestGroupId: UUID,
        role: InterestGroupRole,
        status: MembershipStatus,
    ): List<InterestGroupMembership>

    fun findByIdInterestGroupIdAndStatus(
        interestGroupId: UUID,
        status: MembershipStatus,
    ): List<InterestGroupMembership>

    fun countByIdInterestGroupIdAndStatus(
        interestGroupId: UUID,
        status: MembershipStatus,
    ): Long

    fun countByIdInterestGroupIdAndRoleAndStatus(
        interestGroupId: UUID,
        role: InterestGroupRole,
        status: MembershipStatus,
    ): Long

    @Query(
        """
        SELECT m.id.interestGroupId, COUNT(m)
        FROM InterestGroupMembership m
        WHERE m.id.interestGroupId IN :groupIds
          AND m.status = :status
        GROUP BY m.id.interestGroupId
        """,
    )
    fun countByInterestGroupIdInAndStatus(
        @Param("groupIds") groupIds: Collection<UUID>,
        @Param("status") status: MembershipStatus,
    ): List<Array<Any>>
}
