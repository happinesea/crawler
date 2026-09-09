package com.happinesea.webcrawler.entity;

import com.happinesea.webcrawler.Const.ContentsType;
import com.happinesea.webcrawler.Const.ContractType;
import com.happinesea.webcrawler.Const.DeleteFlg;
import com.happinesea.webcrawler.entity.converter.ContentsTypeConverter;
import com.happinesea.webcrawler.entity.converter.DeleteFlgConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "site_info")
public class SiteInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "site_info_id")
    private Integer siteInfoId;
    
    @Column(name = "site_name")
    private String siteName;
    
    @Column(name = "site_url")
    private String siteUrl;
    
    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "contract_type")
    private String contractType;

	public boolean allowsFullContent() {
		return ContractType.allowsFullContent(contractType);
	}
    
    @Column(name = "delete_flg")
	@Convert(converter = DeleteFlgConverter.class)
    private DeleteFlg deleteFlg;
    
    @Column(name = "contents_type")
	@Convert(converter = ContentsTypeConverter.class)
    private ContentsType contentsType;
}
