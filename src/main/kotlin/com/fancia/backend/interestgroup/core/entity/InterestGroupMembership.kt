package com.fancia.backend.interestgroup.core.entity

import com.fancia.backend.shared.interestgroup.core.enums.InterestGroupRole
import com.fancia.backend.shared.interestgroup.core.enums.MembershipStatus
import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime
import java.util.*

@Embeddable
data class InterestGroupMembershipId(
    @Column(name = "interest_group_id")
    var interestGroupId: UUID,
    @Column(name = "user_id")
    var userId: UUID,
) : Serializable {
    override fun equals(other: Any?): Boolean =
        other is InterestGroupMembershipId &&
                other.interestGroupId == interestGroupId &&
                other.userId == userId

    override fun hashCode(): Int = Objects.hash(interestGroupId, userId)
}

@Entity
@Table(name = "interest_group_membership")
class InterestGroupMembership(
    @EmbeddedId
    var id: InterestGroupMembershipId? = null
) {
    @MapsId("interestGroupId")
    @ManyToOne
    @JoinColumn(name = "interest_group_id", insertable = false, updatable = false)
    var interestGroup: InterestGroup? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    var role: InterestGroupRole = InterestGroupRole.ADMIN
    var joinedAt: LocalDateTime? = null

    @Enumerated(EnumType.STRING)
    var status: MembershipStatus = MembershipStatus.PENDING
}