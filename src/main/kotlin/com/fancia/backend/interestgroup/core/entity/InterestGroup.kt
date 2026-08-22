package com.fancia.backend.interestgroup.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.common.social.core.entity.Link
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "interest_groups")
class InterestGroup : AbstractEntity() {
    @Column(nullable = false, length = 255)
    var name: String = ""

    @Column(nullable = false, length = 255)
    var slug: String = ""

    @Column(nullable = false, length = 4000)
    var description: String = ""

    @OneToMany(mappedBy = "interestGroup", cascade = [CascadeType.ALL], orphanRemoval = true)
    val memberships: MutableSet<InterestGroupMembership> = mutableSetOf<InterestGroupMembership>()

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interest_group_tags", joinColumns = [JoinColumn(name = "interest_group_id")])
    @Column(name = "tag_id")
    var tags: MutableSet<UUID> = mutableSetOf()

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interest_group_links", joinColumns = [JoinColumn(name = "interest_group_id")])
    var links: MutableSet<Link> = mutableSetOf()
}
