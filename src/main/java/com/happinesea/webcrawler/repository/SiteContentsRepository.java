package com.happinesea.webcrawler.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteContents;

@Repository
public interface SiteContentsRepository extends JpaRepository<SiteContents, Long> {
	/**
	 * 添加抓取内容
	 * 
	 * @param urls
	 * @return
	 */
	List<SiteContents> findAllByUrlIn(Collection<String> urls);

	List<SiteContents> findAllByNormalizedUrlHashIn(Collection<String> hashes);

	boolean existsByNormalizedUrlHash(String hash);
	
	/**
	 * 搜索投放内容数据
	 * 
	 * @param category
	 * @param status
	 * @return
	 */
	@Query("select sc from SiteContents sc "
			+ "join fetch sc.siteCategory c "
			+ "left join fetch c.siteInfo "
			+ "where sc.siteCategory = :category and sc.processStatus in :statuses "
			+ "and sc.cmsContentId is null order by sc.siteContentsId asc")
	public List<SiteContents> findContents4Post(@Param("category") SiteCategory category,
			@Param("statuses") Collection<ProcessStatus> statuses);

	@Query("select sc from SiteContents sc "
			+ "join fetch sc.siteCategory c "
			+ "left join fetch c.siteInfo "
			+ "where sc.siteCategory = :category "
			+ "and sc.cmsContentId is not null order by sc.siteContentsId asc")
	public List<SiteContents> findCmsLinkedContents(@Param("category") SiteCategory category);

	@Query("select sc from SiteContents sc "
			+ "join fetch sc.siteCategory c "
			+ "left join fetch c.siteInfo "
			+ "where sc.cmsContentId is not null "
			+ "and (sc.description is null or sc.description = '') "
			+ "order by sc.cmsContentId desc")
	public List<SiteContents> findCmsLinkedContentsWithBlankDescription();

	@Query("select sc from SiteContents sc "
			+ "join fetch sc.siteCategory c "
			+ "left join fetch c.siteInfo "
			+ "where sc.cmsContentId = :cmsContentId")
	public Optional<SiteContents> findByCmsContentIdWithCategory(@Param("cmsContentId") Long cmsContentId);
}
