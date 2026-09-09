package com.happinesea.webcrawler.service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class ProcessOwnerFactory {
	private final String instanceId;

	public ProcessOwnerFactory() {
		this(System.getenv("PROCESS_OWNER_INSTANCE"));
	}

	ProcessOwnerFactory(String configuredInstanceId) {
		this.instanceId = StringUtils.isBlank(configuredInstanceId) ? buildInstanceId() : configuredInstanceId.trim();
	}

	public String newOwner(String jobId) {
		return instanceId + "/" + jobId + "/" + UUID.randomUUID();
	}

	public String instanceId() {
		return instanceId;
	}

	private String buildInstanceId() {
		String host;
		try {
			host = InetAddress.getLocalHost().getHostName();
		} catch (Exception e) {
			host = "unknown-host";
		}
		host = host.length() > 24 ? host.substring(0, 24) : host;
		String pid = ManagementFactory.getRuntimeMXBean().getName().split("@", 2)[0];
		pid = pid.length() > 15 ? pid.substring(0, 15) : pid;
		return host + ":" + pid + ":" + UUID.randomUUID();
	}
}
