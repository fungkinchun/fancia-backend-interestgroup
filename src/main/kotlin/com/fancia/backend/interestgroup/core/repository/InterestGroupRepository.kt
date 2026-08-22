package com.fancia.backend.interestgroup.core.repository

import com.fancia.backend.interestgroup.core.entity.InterestGroup
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface InterestGroupRepository : JpaRepository<InterestGroup, UUID> {
    @Query(
        """
    SELECT g
    FROM InterestGroup g
    WHERE (
        (:name = '' AND :description = '')
        OR trgm_word_similarity(:name, g.name) = true
        OR trgm_word_similarity(:description, g.description) = true
    )
    AND (:filterByTagIds = false OR EXISTS (SELECT tag FROM g.tags tag WHERE tag IN :tagIds))
    GROUP BY g
""",
    )
    fun search(
        @Param("name") name: String,
        @Param("description") description: String,
        @Param("filterByTagIds") filterByTagIds: Boolean,
        @Param("tagIds") tagIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<InterestGroup>

    @Query("SELECT DISTINCT g FROM InterestGroup g JOIN g.tags tag WHERE tag IN :tagIds")
    fun findByTagIdIn(
        @Param("tagIds") tagIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<InterestGroup>

    fun findByIdAndCreatedBy(@Param("id") id: UUID, @Param("createdBy") createdBy: UUID): InterestGroup?

    @Query("SELECT g FROM InterestGroup g WHERE :tagId MEMBER OF g.tags")
    fun findByTagId(@Param("tagId") tagId: UUID): List<InterestGroup>

    fun findBySlug(slug: String): Optional<InterestGroup>

    fun existsBySlug(slug: String): Boolean
}
