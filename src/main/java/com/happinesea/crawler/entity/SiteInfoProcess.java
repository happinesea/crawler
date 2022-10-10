package com.happinesea.crawler.entity;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SiteInfoProcess {
    private @Id Integer siteInfoProcess_id;
    private Integer siteCategoryId;
    private String processStatus;
    private String processId;
    private LocalDate processTime;

}
