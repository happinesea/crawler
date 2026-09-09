package com.happinesea.webcrawler.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.happinesea.webcrawler.Const.ContentsType;
import com.happinesea.webcrawler.Const.DeleteFlg;
import com.happinesea.webcrawler.entity.SiteInfo;

import jakarta.transaction.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SiteInfoRepositoryTest {

    @Autowired
    private SiteInfoRepository repository;

    @Test
    void findActiveSites() {
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

        repository.save(entity1);
        repository.save(entity2);

        
        List<SiteInfo> siteList= repository.findActiveSites();
        assertEquals(1, siteList.size());
        assertEquals("Test Site", siteList.get(0).getSiteName());
    }
}
