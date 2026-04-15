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
    WHERE trgm_word_similarity(:name, g.name) = true
       OR trgm_word_similarity(:description, g.description) = true
       OR trgm_word_similarity(:tags, 
       (SELECT LISTAGG(t, ',') WITHIN GROUP (ORDER BY t) FROM g.tags t)
       ) = true
    GROUP BY g
"""
    )
    fun findAll(
        @Param("name") name: String,
        @Param("description") description: String,
        @Param("tags") tags: String,
        pageable: Pageable
    ): Page<InterestGroup>

    fun findByIdAndCreatedBy(@Param("id") id: UUID, @Param("createdBy") createdBy: UUID): InterestGroup?
    fun findByTagsContaining(tagName: String): List<InterestGroup>
}