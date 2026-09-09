package com.happinesea.webcrawler.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.happinesea.webcrawler.entity.SiteInfo;

@Repository
public interface SiteInfoRepository extends JpaRepository<SiteInfo, Integer> {
	
	/**
	 * 搜索所有有效的目标网站信息
	 * @return
	 */
	@Query("SELECT s FROM SiteInfo s WHERE s.deleteFlg = '0'")
	List<SiteInfo> findActiveSites();
}
