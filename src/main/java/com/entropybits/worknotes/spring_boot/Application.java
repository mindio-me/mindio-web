/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot;

import com.entropybits.worknotes.spring_boot.integration.feishu.FeishuProperties;
import com.entropybits.worknotes.spring_boot.integration.wechat.WechatConfig;
import com.entropybits.worknotes.spring_boot.integration.wechat.WechatInboundConfig;
import com.entropybits.worknotes.spring_boot.integration.reddit.RedditProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({FeishuProperties.class, WechatConfig.class, WechatInboundConfig.class, RedditProperties.class})
public class Application {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(Application.class);
		// ProGuard obfuscation can rename unrelated classes in different packages to the
		// same simple name, which collides under Spring's default (simple-name-only) bean
		// naming. Fully-qualified names stay unique regardless of what ProGuard renames
		// classes to. Set via SpringApplication (not a second @ComponentScan) — stacking
		// @ComponentScan next to @SpringBootApplication's implied one double-scans, since
		// @ComponentScan is @Repeatable.
		app.setBeanNameGenerator(new FullyQualifiedAnnotationBeanNameGenerator());
		app.run(args);
	}

}
