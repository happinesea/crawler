package com.happinesea.webcrawler.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.happinesea.webcrawler.entity.SiteContents;
import com.happinesea.webcrawler.repository.SiteContentsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SiteContentsInsertService {
	private final SiteContentsRepository repository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public SiteContents insertIsolated(SiteContents contents) {
		return repository.saveAndFlush(contents);
	}
}
