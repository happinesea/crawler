package com.happinesea.webcrawler.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteInfo;

@Repository
public interface SiteCategoryRepository extends JpaRepository<SiteCategory, Integer> {
	/**
	 * 搜索目标网站下的所有分类信息
	 * @param siteInfo
	 * @return
	 */
	@Query("SELECT sc FROM SiteCategory sc WHERE sc.siteInfo = :siteInfo AND sc.deleteFlg = '0'")
	List<SiteCategory> findActiveCategoryBySiteInfoId(@Param("siteInfo") SiteInfo siteInfo);

	@EntityGraph(attributePaths = { "siteInfo" })
	@Query("SELECT sc FROM SiteCategory sc WHERE sc.deleteFlg = '0' ORDER BY sc.siteCategoryId ASC")
	List<SiteCategory> findActiveCategories();
}
