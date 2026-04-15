package com.fancia.backend.interestgroup.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import jakarta.persistence.*

@Entity
@Table(name = "interest_group")
class InterestGroup : AbstractEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column(nullable = false)
    var description: String = ""

    @OneToMany(mappedBy = "interestGroup", cascade = [CascadeType.ALL], orphanRemoval = true)
    val memberships: MutableSet<InterestGroupMembership> = mutableSetOf<InterestGroupMembership>()

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable
    @Column(length = 100)
    val tags: MutableSet<String> = mutableSetOf()
}
