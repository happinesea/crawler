package com.happinesea.webcrawler.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.CollectionUtils;

import com.happinesea.webcrawler.Const.ContentsType;
import com.happinesea.webcrawler.Const.DeleteFlg;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteInfo;

import jakarta.transaction.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SiteCategoryRepositoryTest {

    @Autowired
    private SiteInfoRepository siteInfoRepository;
    @Autowired
    private SiteCategoryRepository categoryRepository;
	@Test
	void testFindActiveCategoryBySiteInfoId() {

        var entity1 = new SiteInfo();
        entity1.setSiteName("Test Site");
        entity1.setSiteUrl("https://example.com");
        entity1.setDeleteFlg(DeleteFlg.OFF);
        entity1.setContentsType(ContentsType.HTML);
        var entity2 = new SiteInfo();
        entity2.setSiteName("Test Site2");
        entity2.setSiteUrl("https://example2.com");
        entity2.setDeleteFlg(DeleteFlg.ON);
        entity2.setContentsType(ContentsType.HTML);

        siteInfoRepository.save(entity1);
        siteInfoRepository.save(entity2);
        
        var category1 = new SiteCategory();
        category1.setSiteInfo(entity1);
        category1.setCategoryName("test1");
        category1.setDeleteFlg(DeleteFlg.OFF);
        category1.setCmsCategoryId(7);
        category1.setCmsTargetCategory("10,11");
        category1.setMoreBodySelectTxt("記事全文を読む,続きを読む");
        var category2 = new SiteCategory();
        category2.setSiteInfo(entity1);
        category2.setCategoryName("test2");
        category2.setDeleteFlg(DeleteFlg.ON);

        categoryRepository.save(category1);
        categoryRepository.save(category2);
        
        // empty
        List<SiteCategory> result1 = categoryRepository.findActiveCategoryBySiteInfoId(entity1);
        // 1 item
        List<SiteCategory> result2 = categoryRepository.findActiveCategoryBySiteInfoId(entity2);
        
        assertEquals(1, result1.size());
        assertTrue(CollectionUtils.isEmpty(result2));
        
        assertEquals("test1", result1.get(0).getCategoryName());
        assertEquals(7, result1.get(0).getCmsCategoryId());
        assertEquals("10,11", result1.get(0).getCmsTargetCategory());
        assertEquals("記事全文を読む,続きを読む", result1.get(0).getMoreBodySelectTxt());
	}

}
