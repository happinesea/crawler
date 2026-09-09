package com.happinesea.webcrawler.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.entity.SiteInfoProcessPool;

@Repository
public interface SiteInfoProcessRepository extends JpaRepository<SiteInfoProcessPool, Integer> {
	@EntityGraph(attributePaths = { "siteCategory", "siteCategory.siteInfo" })
	List<SiteInfoProcessPool> findByProcessStatusNot(ProcessStatus status);

	@EntityGraph(attributePaths = { "siteCategory", "siteCategory.siteInfo" })
	List<SiteInfoProcessPool> findByProcessStatusNotOrderByProcessTimeAscSiteInfoProcessIdAsc(ProcessStatus status);

	@EntityGraph(attributePaths = { "siteCategory", "siteCategory.siteInfo" })
	List<SiteInfoProcessPool> findByProcessStatusInOrderByProcessTimeAscSiteInfoProcessIdAsc(
			Collection<ProcessStatus> statuses);

	@EntityGraph(attributePaths = { "siteCategory", "siteCategory.siteInfo" })
	@Query("""
			select p
			from SiteInfoProcessPool p
			where p.processStatus in :availableStatuses
			   or (p.processStatus = :processingStatus and coalesce(p.heartbeatAt, p.processTime) < :staleBefore)
			order by case when p.processTime is null then 0 else 1 end, p.processTime asc, p.siteInfoProcessId asc
			""")
	List<SiteInfoProcessPool> findClaimCandidates(
			@Param("availableStatuses") Collection<ProcessStatus> availableStatuses,
			@Param("processingStatus") ProcessStatus processingStatus,
			@Param("staleBefore") java.time.LocalDateTime staleBefore);

	@EntityGraph(attributePaths = { "siteCategory", "siteCategory.siteInfo" })
	@Query("select p from SiteInfoProcessPool p where p.siteInfoProcessId = :id")
	Optional<SiteInfoProcessPool> findByIdWithCategory(@Param("id") Integer id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query("""
			update SiteInfoProcessPool p
			set p.processStatus = :processingStatus,
			    p.processId = :owner,
			    p.ownerInstance = :ownerInstance,
			    p.leaseAttempt = p.leaseAttempt + 1,
			    p.claimedAt = :now,
			    p.heartbeatAt = :now,
			    p.processTime = :now
			where p.siteInfoProcessId = :id
			  and (
			       p.processStatus in :availableStatuses
			       or (p.processStatus = :processingStatus and coalesce(p.heartbeatAt, p.processTime) < :staleBefore)
			  )
			""")
	int claimForProcessing(
			@Param("id") Integer id,
			@Param("owner") String owner,
			@Param("ownerInstance") String ownerInstance,
			@Param("now") java.time.LocalDateTime now,
			@Param("availableStatuses") Collection<ProcessStatus> availableStatuses,
			@Param("processingStatus") ProcessStatus processingStatus,
			@Param("staleBefore") java.time.LocalDateTime staleBefore);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query("""
			update SiteInfoProcessPool p
			set p.processStatus = :nextStatus,
			    p.lastResultStatus = :resultStatus,
			    p.processId = null,
			    p.ownerInstance = null,
			    p.claimedAt = null,
			    p.heartbeatAt = null,
			    p.processTime = :now
			where p.siteInfoProcessId = :id
			  and p.processStatus = :processingStatus
			  and p.processId = :owner
			  and p.leaseAttempt = :leaseAttempt
			""")
	int finishOwnedProcessing(
			@Param("id") Integer id,
			@Param("owner") String owner,
			@Param("leaseAttempt") long leaseAttempt,
			@Param("nextStatus") ProcessStatus nextStatus,
			@Param("resultStatus") ProcessStatus resultStatus,
			@Param("processingStatus") ProcessStatus processingStatus,
			@Param("now") java.time.LocalDateTime now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query("""
			update SiteInfoProcessPool p
			set p.heartbeatAt = :now,
			    p.processTime = :now
			where p.siteInfoProcessId = :id
			  and p.processStatus = :processingStatus
			  and p.processId = :owner
			  and p.leaseAttempt = :leaseAttempt
			""")
	int heartbeatOwnedProcessing(
			@Param("id") Integer id,
			@Param("owner") String owner,
			@Param("leaseAttempt") long leaseAttempt,
			@Param("processingStatus") ProcessStatus processingStatus,
			@Param("now") java.time.LocalDateTime now);
}
